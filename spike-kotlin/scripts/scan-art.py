#!/usr/bin/env python3
"""Scanneur ART pour les piles du spike Kotlin (§7.a du prompt de mission).

Analyse les pools de constantes des .class d'un jar (miroir de la méthode
du spike Java, ScanArt.kt) et signale :
  - les références à des classes/méthodes JDK sensibles pour ART
    (absentes d'android.jar API 26+, ou à comportement divergent) ;
  - le comptage de méthodes DÉCLARÉES (borne basse du comptage dex) ;
  - les plages de versions de class-file (dexage d8 : > 52 exige --release).

Limites documentées (miroir E3 de l'étude Java) : les usages réflexifs
sont invisibles ; le comptage de méthodes est une borne basse (méthodes
déclarées, pas référencées) ; la présence d'une référence n'est pas une
preuve d'exécution — la liste est un filtre d'audit, pas un verdict.

Usage : python3 scan-art.py <jar> [<jar>...]
"""
import re
import struct
import sys
import zipfile

MOTIFS_SENSIBLES = [
    # (motif regex sur FQCN/membre, catégorie)
    (r"^sun/misc/Unsafe", "sun.misc.Unsafe"),
    (r"^jdk/internal/misc/Unsafe", "jdk.internal.misc.Unsafe"),
    (r"^java/lang/invoke/MethodHandles\$Lookup", "MethodHandles.Lookup (ART 26+ : OK, à vérifier)"),
    (r"^java/lang/invoke/VarHandle", "VarHandle (API 33+)"),
    (r"^java/lang/invoke/MethodHandle", "MethodHandle"),
    (r"^java/awt", "java.awt"),
    (r"^javax/swing", "javax.swing"),
    (r"^java/beans/Introspector", "java.beans.Introspector"),
    (r"^java/lang/management", "java.lang.management"),
    (r"^sun/nio/ch/DirectBuffer", "sun.nio.ch.DirectBuffer (cleaner)"),
    (r"^java/nio/file/.*/(Files|Path)", "java.nio.file (API 26+ : partiel)"),
    (r"^java/lang/Runtime\$Version", "Runtime$Version (API 26+ absent — shim)"),
    (r"^java/lang/StackWalker", "StackWalker (API 34+)"),
    (r"^java/lang/ClassValue", "ClassValue"),
    (r"^java/rmi", "java.rmi"),
    (r"^javax/naming", "javax.naming"),
    (r"^java/lang/Module", "java.lang.Module (JPMS, absent ART)"),
    (r"^java/lang/constant/", "java.lang.constant (API 35+)"),
    (r"^java/util/concurrent/Flow", "Flow (API 35+)"),
]

# Motifs à CORRÉLER classe déclarante ↔ nom de membre : le nom de méthode
# SEUL est ambigu dans le pool de constantes (l'Utf8 du nom ne porte pas sa
# classe). Exemple réel (vague 1b) : Files.readAllBytes est Java 7 / ART 26+
# (légitime), SEUL InputStream.readAllBytes est API 33+ — la référence n'est
# sensible que si le DÉCLARANT (Methodref/InterfaceMethodref) correspond au
# motif de classe ET le nom au motif de membre.
MOTIFS_MEMBRES = [
    (re.compile(r"^java/io/InputStream$"), re.compile(r"^readAllBytes$"), "InputStream.readAllBytes (API 33+)"),
    (re.compile(r"^java/io/InputStream$"), re.compile(r"^readNBytes$"), "InputStream.readNBytes (API 33+)"),
    # Famille SequencedCollection sur java.util.List (JDK 21 — née de
    # l'incident appareil du 30/10 : NoSuchMethodError List.removeLast sur
    # l'openjdk 17 de Termux, invisible en CI JDK 21). Le DECLARANT seul
    # rend la référence sensible : Deque/ArrayDeque/LinkedList portent ces
    # méthodes depuis Java 6, kotlin.collections.ArrayDeque est une
    # implémentation Kotlin pure — aucun des trois n'est sensible.
    (re.compile(r"^java/util/(List|SequencedCollection)$"),
     re.compile(r"^(addFirst|addLast|getFirst|getLast|removeFirst|removeLast|reversed)$"),
     "java.util.List.<SequencedCollection> (JDK 21 — absent JDK 17/ART)"),
]

# Catégorie à VÉRIFIER (pas interdite d'office) : java.util.ServiceLoader
# est PRÉSENT sur ART et est le mécanisme déclaratif du SPI du pack langue
# (D6 — le SPI charge le moteur, miroir de l'ancien). La vérification
# prescrite par la catégorie : le jar qui référence ServiceLoader doit
# porter un descripteur META-INF/services dont chaque ligne est un nom
# binaire valide ET résoluble en .class dans le MÊME jar (sinon la référence
# reste un motif sensible — un descripteur cassé casse le chargement au
# runtime).
MOTIF_SERVICELOADER = re.compile(r"^java/util/ServiceLoader")
NOM_BINAIRE = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$")

MOTIFS = [(re.compile(m), c) for m, c in MOTIFS_SENSIBLES]


def scan_classe(donnees: bytes) -> tuple[int, int, list[str], int]:
    """Rend (version_majeure, nb_méthodes, références sensibles, réf. ServiceLoader)."""
    if donnees[:4] != b"\xca\xfe\xba\xbe":
        return 0, 0, [], 0
    mineure, majeure = struct.unpack(">HH", donnees[4:8])
    nb_const = struct.unpack(">H", donnees[8:10])[0]
    i = 10
    constantes: list[bytes | None] = [None] * nb_const
    # Structures gardées pour la corrélation classe↔membre (MOTIFS_MEMBRES) :
    classes: dict[int, int] = {}  # tag 7 (Class) → indice Utf8 du nom
    nats: dict[int, tuple[int, int]] = {}  # tag 12 (NameAndType) → (nom, desc)
    refs_methodes: list[tuple[int, int]] = []  # tags 10/11 (Methodref) → (classe, nat)
    idx = 1
    while idx < nb_const:
        tag = donnees[i]
        i += 1
        if tag == 1:  # Utf8 : u2 longueur + octets
            lng = struct.unpack(">H", donnees[i : i + 2])[0]
            constantes[idx] = donnees[i + 2 : i + 2 + lng]
            i += 2 + lng
        elif tag == 7:  # Class : u2 indice Utf8 du nom
            classes[idx] = struct.unpack(">H", donnees[i : i + 2])[0]
            i += 2
        elif tag == 12:  # NameAndType : u2 nom + u2 descripteur
            nats[idx] = struct.unpack(">HH", donnees[i : i + 4])
            i += 4
        elif tag in (10, 11):  # Methodref/InterfaceMethodref : u2 classe + u2 nat
            refs_methodes.append(struct.unpack(">HH", donnees[i : i + 4]))
            i += 4
        elif tag in (8, 16, 19, 20):  # String, MethodType, Module, Package : u2
            i += 2
        elif tag == 15:  # MethodHandle : u1 kind + u2 index
            i += 3
        elif tag in (9, 3, 4, 17, 18):  # Fieldref/Int/Float/Dynamic/InvokeDynamic : u4
            i += 4
        elif tag in (5, 6):  # Long, Double : u8, DEUX entrées
            constantes[idx] = None
            constantes[idx + 1] = None
            i += 8
            idx += 1
        else:
            # tag inconnu : abandon honnête de la classe (jamais levé)
            return majeure, 0, [], 0
        idx += 1

    refs: list[str] = []
    refs_service_loader = 0
    for c in constantes:
        if c is None:
            continue
        try:
            texte = c.decode("utf-8", errors="replace")
        except Exception:
            continue
        if MOTIF_SERVICELOADER.search(texte):
            refs_service_loader += 1
            continue
        for motif, categorie in MOTIFS:
            if motif.search(texte):
                refs.append(f"{categorie} :: {texte[:120]}")
                break

    def utf8(indice: int) -> str:
        c = constantes[indice] if 0 < indice < len(constantes) else None
        if c is None:
            return ""
        return c.decode("utf-8", errors="replace")

    # Motifs MEMBRES corrélés : le déclarant (Methodref) doit correspondre au
    # motif de classe ET le nom au motif de membre (le nom seul est ambigu).
    for class_idx, nat_idx in refs_methodes:
        nat = nats.get(nat_idx)
        if nat is None:
            continue
        nom = utf8(nat[0])
        classe_decl = utf8(classes.get(class_idx, 0))
        if not nom or not classe_decl:
            continue
        for motif_cls, motif_nom, categorie in MOTIFS_MEMBRES:
            if motif_nom.match(nom) and motif_cls.match(classe_decl):
                refs.append(f"{categorie} :: {classe_decl}.{nom}")
                break

    # Comptage de méthodes : re-parse léger de la table des méthodes.
    nb_methodes = compter_methodes(donnees, i, nb_const, constantes)
    return majeure, nb_methodes, refs, refs_service_loader


def compter_methodes(donnees: bytes, i: int, nb_const: int, constantes) -> int:
    """Compte les méthodes DÉCLARÉES (après access_flags de la classe)."""
    try:
        # i pointe après le pool ; access_flags(2) this(2) super(2) ifaces(2)
        i += 2
        idx_this = struct.unpack(">H", donnees[i : i + 2])[0]
        i += 2
        i += 2  # super
        nb_ifaces = struct.unpack(">H", donnees[i : i + 2])[0]
        i += 2 + 2 * nb_ifaces
        nb_champs = struct.unpack(">H", donnees[i : i + 2])[0]
        i += 2
        for _ in range(nb_champs):
            i += 6
            lng = struct.unpack(">H", donnees[i : i + 2])[0]
            i += 2 + lng
            nb_attrs = struct.unpack(">H", donnees[i : i + 2])[0]
            i += 2
            for _ in range(nb_attrs):
                i += 2
                alng = struct.unpack(">I", donnees[i : i + 4])[0]
                i += 4 + alng
        nb_methodes = struct.unpack(">H", donnees[i : i + 2])[0]
        return nb_methodes
    except Exception:
        return 0


def verifier_descripteurs_services(zf: zipfile.ZipFile) -> tuple[bool, str]:
    """Vérifie les descripteurs META-INF/services d'un jar qui référence
    ServiceLoader : chaque descripteur doit exister, être non vide, et
    chacune de ses lignes doit être un nom binaire valide résoluble en
    .class DANS LE MÊME jar (un fournisseur absent casse le chargement au
    runtime — c'est la vérification prescrite par la catégorie).

    Rend (ok, trace lisible).
    """
    prefixe = "META-INF/services/"
    entrees = [n for n in zf.namelist() if n.startswith(prefixe) and len(n) > len(prefixe)]
    if not entrees:
        return False, "aucun descripteur META-INF/services dans le jar"
    classes = {n[:-6] for n in zf.namelist() if n.endswith(".class")}
    details: list[str] = []
    for entree in entrees:
        lignes: list[str] = []
        for brut in zf.read(entree).decode("utf-8", errors="replace").splitlines():
            ligne = brut.strip()
            if not ligne or ligne.startswith("#"):
                continue
            lignes.append(ligne)
        if not lignes:
            return False, f"descripteur vide : {entree}"
        for ligne in lignes:
            if not NOM_BINAIRE.match(ligne):
                return False, f"ligne invalide : {entree} → {ligne[:80]}"
            if ligne.replace(".", "/") not in classes:
                return False, f"fournisseur absent du jar : {entree} → {ligne[:80]}"
        details.append(f"{entree} ({len(lignes)} fournisseur(s))")
    return True, "; ".join(details)


def main(jars: list[str]) -> int:
    for jar in jars:
        classes = 0
        methodes = 0
        versions: dict[int, int] = {}
        sensibles: dict[str, int] = {}
        exemples: dict[str, str] = {}
        refs_service_loader = 0
        with zipfile.ZipFile(jar) as zf:
            for nom in zf.namelist():
                if not nom.endswith(".class"):
                    continue
                donnees = zf.read(nom)
                majeure, nbm, refs, nb_sl = scan_classe(donnees)
                classes += 1
                methodes += nbm
                refs_service_loader += nb_sl
                versions[majeure] = versions.get(majeure, 0) + 1
                for r in refs:
                    cle = r.split(" :: ")[0]
                    sensibles[cle] = sensibles.get(cle, 0) + 1
                    exemples.setdefault(cle, r.split(" :: ")[1])
            trace_service_loader = ""
            if refs_service_loader > 0:
                ok, trace = verifier_descripteurs_services(zf)
                if ok:
                    trace_service_loader = (
                        f"  ServiceLoader : OK — {refs_service_loader} référence(s), "
                        f"descripteur(s) vérifié(s) : {trace}"
                    )
                else:
                    sensibles["ServiceLoader (référence SANS descripteur valide)"] = refs_service_loader
                    exemples["ServiceLoader (référence SANS descripteur valide)"] = "java/util/ServiceLoader"
                    trace_service_loader = f"  ServiceLoader : ÉCHEC de vérification — {trace}"
        print(f"=== {jar.split('/')[-1]} ===")
        print(f"classes analysées : {classes}")
        print(f"méthodes déclarées (borne basse dex) : {methodes}")
        print("versions de class-file (majeure:nombre) : " + ", ".join(f"{v}:{n}" for v, n in sorted(versions.items())))
        if sensibles:
            print("références sensibles ART :")
            for cle, n in sorted(sensibles.items(), key=lambda x: -x[1]):
                print(f"  {n:6d} × {cle}   ex: {exemples[cle][:80]}")
        else:
            print("références sensibles ART : AUCUNE")
        if trace_service_loader:
            print(trace_service_loader)
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
