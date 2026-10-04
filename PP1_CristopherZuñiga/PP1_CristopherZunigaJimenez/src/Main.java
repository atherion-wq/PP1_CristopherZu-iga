import compiler.Lexer;
import compiler.Parser;
import compiler.sym;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java_cup.runtime.Symbol;

public class Main {

    public static File pedirArchivoTxt() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecciona tu archivo fuente");


        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("Archivos de texto (*.txt)", "txt"));

        int resultado = chooser.showOpenDialog(null);

        if (resultado == JFileChooser.APPROVE_OPTION) {
            File archivo = chooser.getSelectedFile();
            if (archivo.getName().toLowerCase().endsWith(".txt")) {
                return archivo;
            }
        }
        return null;
    }


    private static String obtenerTablaSimbolos(String nombreToken) {

        if (nombreToken.equals("IDENTIFICADOR")) {
            return "TS_IDENTIFICADORES" ;
        }


        switch (nombreToken){
            case "Cero" :
            case "ENTERO_POSITIVO":
            case "LITERAL_FLOAT":
            case "LITERAL_CHAR":
            case "LITERAL_STRING":
            case "TRUE":
            case "FALSE":
                return "TS_LITERALES";
        }

        switch (nombreToken) {
            case "VAL":
            case "INT":
            case "FLOAT":
            case "CHAR":
            case "BOOL":
            case "VOID":
            case "MAIN":
            case "IF":
            case "ELSE":
            case "FOR":
            case "WHILE":
            case "BREAK":
            case "CONTINUE":
            case "RETURN":
                return "TS_RESERVADAS";
        }

        switch (nombreToken) {

            case "ASIGNACION":
            case "MAS":
            case "MENOS":
            case "MULTIPLICACION":
            case "DIVISION":
            case "DIVISION_ENTERA":
            case "MODULO":
            case "POTENCIA":
            case "INCREMENTO":
            case "DECREMENTO":
            case "NEGACION":
            case "MENOR":
            case "MAYOR":
            case "MENOR_IGUAL":
            case "MAYOR_IGUAL":
            case "IGUAL":
            case "DIFERENTE":
                return "TS_OPERADORES";
        }

        switch (nombreToken) {

            case "PARENTESIS_ABRE":
            case "PARENTESIS_CIERRA":
            case "BLOQUE_ABRE":
            case "BLOQUE_CIERRA":
            case "CORCHETE_ABRE":
            case "CORCHETE_CIERRA":
            case "DELIMITADOR":
            case "COMA":
                return "TS_DELIMITADORES";
        }
        return "TS_OTROS";
    }


    public static void creacionTokens(String archivoFuente, String archivoTokens) throws IOException {

        Lexer lexerTokens = new Lexer(new FileReader(archivoFuente));

        String scope = "GLOBAL";
        int profundidadBloques = 0;
        int bloqueFuncion = -1;

        boolean esperandoNombreFuncion = false;
        boolean esperandoMain = true;

        int tokenAnterior = -1;

        try (BufferedWriter writer = Files.newBufferedWriter(
                Path.of(archivoTokens),
                StandardCharsets.UTF_8)) {

            writer.write(String.format(
                    "%-25s %-30s %-20s %-15s %-10s %-10s%n",
                    "TOKEN",
                    "LEXEMA",
                    "TABLA_SÍMBOLOS",
                    "SCOPE",
                    "LÍNEA",
                    "COLUMNA"
            ));

            writer.write(
                    "--------------------------------------------------------------------------------------------------------------"
            );
            writer.newLine();

            Symbol token;

            do {

                token = lexerTokens.next_token();

                if (token.sym == sym.EOF) {
                    break;
                }

                String nombreToken;

                if (token.sym >= 0 &&
                        token.sym < sym.terminalNames.length) {

                    nombreToken = sym.terminalNames[token.sym];

                } else {

                    nombreToken = "TOKEN_" + token.sym;
                }


                String lexema = "";
                if (token.value  != null) {
                    lexema = token.value.toString();
                }

                String scopeToken = scope;


                if (token.sym == sym.MAIN &&
                        esperandoMain) {

                    scopeToken = "GLOBAL";
                    scope = "main";
                    bloqueFuncion = -1;
                    esperandoMain = false;
                }

                if (scope.equals("GLOBAL")) {
                    if ((token.sym == sym.INT ||
                            token.sym == sym.FLOAT ||
                            token.sym == sym.CHAR ||
                            token.sym == sym.BOOL)
                            && tokenAnterior != sym.VAL) {

                        esperandoNombreFuncion = true;
                    }
                }

                if (token.sym == sym.IDENTIFICADOR &&
                        esperandoNombreFuncion) {

                    scopeToken = "GLOBAL";
                    scope = lexema;
                    bloqueFuncion = -1;
                    esperandoNombreFuncion = false;
                }


                String tablaSimbolos =
                        obtenerTablaSimbolos(nombreToken);


                writer.write(String.format(
                        "%-25s %-30s %-20s %-15s %-10d %-10d%n",
                        nombreToken,
                        lexema,
                        tablaSimbolos,
                        scopeToken,
                        token.left,
                        token.right
                ));


                if (token.sym == sym.BLOQUE_ABRE) {

                    profundidadBloques++;

                    if (!scope.equals("GLOBAL") &&
                            bloqueFuncion == -1) {

                        bloqueFuncion = profundidadBloques;
                    }
                }

                if (token.sym == sym.BLOQUE_CIERRA) {

                    int nivelAntesDeCerrar =
                            profundidadBloques;

                    profundidadBloques--;

                    if (!scope.equals("GLOBAL") &&
                            nivelAntesDeCerrar == bloqueFuncion) {

                        scope = "GLOBAL";
                        bloqueFuncion = -1;
                    }
                }

                tokenAnterior = token.sym;

            } while (true);
        }
    }

    public static void main(String[] args) throws IOException {

        File archivo = pedirArchivoTxt();
        String archivoFuente ="";

        if (archivo != null) {
            archivoFuente = String.valueOf(archivo.toPath());
        } else {
            System.out.println("No se seleccionó ningún archivo.");
            System.exit(0);
        }

        String archivoTokens = "tokens.txt";

        try {
            creacionTokens(archivoFuente, archivoTokens);

            Lexer lexerParser = new Lexer(
                    new FileReader(archivoFuente)
            );

            Parser parser = new Parser(lexerParser);

            parser.parse();

            System.out.println(
                    "Archivo de tokens generado correctamente: "
                            + archivoTokens
            );

            if (parser.errores.isEmpty()) {
                System.out.println("Programa sintácticamente correcto.");
            } else {
                parser.errores.forEach(System.err::println);

                System.err.println(
                        "El archivo fuente NO PUEDE ser generado correctamente por la gramática."
                );


            }

        } catch (Exception e) {
            System.err.println(
                    "Error durante el análisis: " + e.getMessage()
            );
        }
    }
}