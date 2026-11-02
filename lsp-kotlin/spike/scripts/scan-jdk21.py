#!/usr/bin/env python3
"""scan-jdk21.py — signale les références à des méthodes JDK 21+ de la famille
SequencedCollection (absentes de java.util.List avant Java 21 — ET d'android.jar)
dans les .class d'un jar ou d'un répertoire de classes.

Motivation : java.lang.NoSuchMethodError: java.util.List.removeLast() observé
sur appareil (Termux openjdk 17) avec kotlin-1.0.0.jar — le bytecode 61 peut
référencer une méthode JDK 21 (jvmTarget ne borne pas le pool de constantes).

Receveurs sensibles : java/util/List, java/util/SequencedCollection.
NON sensibles : java/util/Deque, java/util/ArrayDeque, java/util/LinkedList
(ces méthodes y existent depuis Java 6), kotlin/collections/ArrayDeque
(implémentation Kotlin pure — membre propre, aucun JDK requis).

Parseur : miroir de scan-art.py (dictionnaires classes/nats/refs + corrélation
déclarant↔membre, abandon honnête de la classe sur tag inconnu).

Usage : python3 scan-jdk21.py <jar-ou-dossier> [...]   — rc=1 si référence trouvée.
"""
import os
import struct
import sys
import zipfile

METHODES_SENSIBLES = {
    "addFirst", "addLast", "getFirst", "getLast",
    "removeFirst", "removeLast", "reversed",
}
RECEPTEURS_SENSIBLES = {"java/util/List", "java/util/SequencedCollection"}


def scanner_class(donnees: bytes):
    """Rend la liste (receveur, méthode) des références sensibles du pool."""
    if donnees[:4] != b"\xca\xfe\xba\xbe":
        return None  # pas un class-file
    nb_const = struct.unpack(">H", donnees[8:10])[0]
    i = 10
    utf8s = {}  # indice → texte
    classes = {}  # tag 7 (Class) → indice Utf8 du nom
    nats = {}  # tag 12 (NameAndType) → (nom, desc)
    refs = []  # tags 10/11 (Methodref) → (classe, nat)
    idx = 1
    while idx < nb_const:
        tag = donnees[i]
        i += 1
        if tag == 1:  # Utf8 : u2 longueur + octets
            lng = struct.unpack(">H", donnees[i : i + 2])[0]
            utf8s[idx] = donnees[i + 2 : i + 2 + lng].decode("utf-8", "replace")
            i += 2 + lng
        elif tag == 7:  # Class : u2 indice Utf8 du nom
            classes[idx] = struct.unpack(">H", donnees[i : i + 2])[0]
            i += 2
        elif tag == 12:  # NameAndType : u2 nom + u2 descripteur
            nats[idx] = struct.unpack(">HH", donnees[i : i + 4])
            i += 4
        elif tag in (10, 11):  # Methodref/InterfaceMethodref : u2 classe + u2 nat
            refs.append(struct.unpack(">HH", donnees[i : i + 4]))
            i += 4
        elif tag in (8, 16, 19, 20):  # String, MethodType, Module, Package : u2
            i += 2
        elif tag == 15:  # MethodHandle : u1 kind + u2 index
            i += 3
        elif tag in (9, 3, 4, 17, 18):  # Fieldref/Int/Float/Dynamic/InvokeDynamic : u4
            i += 4
        elif tag in (5, 6):  # Long, Double : u8, DEUX entrées
            i += 8
            idx += 1
        else:
            return None  # tag inconnu : abandon honnête (jamais levé)
        idx += 1

    trouves = []
    for ci, ni in refs:
        nom_classe = utf8s.get(classes.get(ci, -1))
        nat = nats.get(ni)
        if nom_classe in RECEPTEURS_SENSIBLES and nat:
            methode = utf8s.get(nat[0])
            if methode in METHODES_SENSIBLES:
                trouves.append((nom_classe, methode))
    return trouves


def scanner_chemin(chemin):
    total = {}
    if os.path.isdir(chemin):
        for racine, _, fichiers in os.walk(chemin):
            for f in sorted(fichiers):
                if f.endswith(".class"):
                    p = os.path.join(racine, f)
                    with open(p, "rb") as fh:
                        for (c, m) in scanner_class(fh.read()) or []:
                            total.setdefault((c, m), []).append(p)
    elif zipfile.is_zipfile(chemin):
        with zipfile.ZipFile(chemin) as z:
            for info in z.infolist():
                if info.filename.endswith(".class"):
                    for (c, m) in scanner_class(z.read(info)) or []:
                        total.setdefault((c, m), []).append(info.filename)
    return total


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    echecs = 0
    for chemin in argv[1:]:
        total = scanner_chemin(chemin)
        if total:
            echecs += 1
            print(f"ÉCHOUÉ {chemin} — références JDK 21+ (SequencedCollection) :")
            for (c, m), fichiers in sorted(total.items()):
                print(f"  {c}.{m} — {len(fichiers)} classe(s) : "
                      + ", ".join(sorted(set(fichiers))[:8]))
        else:
            print(f"OK {chemin} — aucune référence JDK 21+ (SequencedCollection).")
    return 1 if echecs else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
