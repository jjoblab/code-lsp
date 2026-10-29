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
    (r"readAllBytes", "InputStream.readAllBytes (API 33+)"),
    (r"readNBytes", "InputStream.readNBytes (API 33+)"),
    (r"^java/lang/ClassValue", "ClassValue"),
    (r"^java/rmi", "java.rmi"),
    (r"^javax/naming", "javax.naming"),
    (r"^java/util/ServiceLoader", "ServiceLoader (présent ART — vérifier META-INF/services)"),
    (r"^java/lang/Module", "java.lang.Module (JPMS, absent ART)"),
    (r"^java/lang/constant/", "java.lang.constant (API 35+)"),
    (r"^java/util/concurrent/Flow", "Flow (API 35+)"),
]

MOTIFS = [(re.compile(m), c) for m, c in MOTIFS_SENSIBLES]


def scan_classe(donnees: bytes) -> tuple[int, int, list[str]]:
    """Rend (version_majeure, nb_méthodes, références sensibles) d'un class-file."""
    if donnees[:4] != b"\xca\xfe\xba\xbe":
        return 0, 0, []
    mineure, majeure = struct.unpack(">HH", donnees[4:8])
    nb_const = struct.unpack(">H", donnees[8:10])[0]
    i = 10
    constantes: list[bytes | None] = [None] * nb_const
    idx = 1
    while idx < nb_const:
        tag = donnees[i]
        i += 1
        if tag == 1:  # Utf8 : u2 longueur + octets
            lng = struct.unpack(">H", donnees[i : i + 2])[0]
            constantes[idx] = donnees[i + 2 : i + 2 + lng]
            i += 2 + lng
        elif tag in (7, 8, 16, 19, 20):  # Class, String, MethodType, Module, Package : u2
            constantes[idx] = None
            i += 2
        elif tag == 15:  # MethodHandle : u1 kind + u2 index
            constantes[idx] = None
            i += 3
        elif tag in (9, 10, 11, 3, 4, 12, 17, 18):  # Fieldref/Methodref/InterfaceMethodref/Int/Float/NameAndType/Dynamic/InvokeDynamic : u4
            constantes[idx] = None
            i += 4
        elif tag in (5, 6):  # Long, Double : u8, DEUX entrées
            constantes[idx] = None
            constantes[idx + 1] = None
            i += 8
            idx += 1
        else:
            # tag inconnu : abandon honnête de la classe (jamais levé)
            return majeure, 0, []
        idx += 1

    refs: list[str] = []
    for c in constantes:
        if c is None:
            continue
        try:
            texte = c.decode("utf-8", errors="replace")
        except Exception:
            continue
        for motif, categorie in MOTIFS:
            if motif.search(texte):
                refs.append(f"{categorie} :: {texte[:120]}")
                break
    # Comptage de méthodes : re-parse léger de la table des méthodes.
    nb_methodes = compter_methodes(donnees, i, nb_const, constantes)
    return majeure, nb_methodes, refs


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


def main(jars: list[str]) -> int:
    for jar in jars:
        classes = 0
        methodes = 0
        versions: dict[int, int] = {}
        sensibles: dict[str, int] = {}
        exemples: dict[str, str] = {}
        with zipfile.ZipFile(jar) as zf:
            for nom in zf.namelist():
                if not nom.endswith(".class"):
                    continue
                donnees = zf.read(nom)
                majeure, nbm, refs = scan_classe(donnees)
                classes += 1
                methodes += nbm
                versions[majeure] = versions.get(majeure, 0) + 1
                for r in refs:
                    cle = r.split(" :: ")[0]
                    sensibles[cle] = sensibles.get(cle, 0) + 1
                    exemples.setdefault(cle, r.split(" :: ")[1])
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
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
