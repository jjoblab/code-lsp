package jo.codeide.lsp.java

/** Corpus communs des tests du cahier (étage N1) — sources témoin des fiches. */
object CorpusCommun {

    /** MAIN_OK du cahier (fiche 013) : code valide, 0 erreur attendue. */
    val MAIN_OK = """
        public class Main {
            public static void main(String[] args) {
                String message = "Hello, " + args.length;
                System.out.println(message);
            }
        }
    """.trimIndent()

    /** MAIN_ERR du cahier (fiches 012/034) : mismatch de type, au moins 1 erreur attendue. */
    val MAIN_ERR = """
        public class Main {
            public static void main(String[] args) {
                String s = 42;
            }
        }
    """.trimIndent()
}
