import compiler.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        try (BufferedReader reader = Files.newBufferedReader(Path.of(archivoFuente))) {
            Parser parser = new Parser(new Lexer(reader));
            analizarYGenerarTokens(archivoFuente, archivoTokens, parser);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Error durante el análisis", e);
        }
    }

    private static Nodo analizarYGenerarTokens(String archivoFuente, String archivoTokens,
                                               Parser parser) throws Exception {
        try {
            Symbol resultado = parser.parse();

            if (resultado != null && resultado.value instanceof Nodo raiz) {
                if (raiz.clase == Nodo.Clase.PROGRAMA) {
                    return raiz;
                }
            }

            return null;

        } finally {
            escribirTokens(archivoFuente, archivoTokens, parser.scopes);
        }
    }

    private static void escribirTokens(String archivoFuente, String archivoTokens,
                                       List<Parser.Scope> scopes) throws IOException {
        Map<Long, Parser.Scope> aperturas = new HashMap<>();
        for (Parser.Scope scope : scopes) {
            aperturas.put(scope.inicio, scope);
        }

        Deque<Parser.Scope> activos = new ArrayDeque<>();
        Deque<String> rutas = new ArrayDeque<>();
        Map<String, Integer> contadorBloques = new HashMap<>();
        rutas.push("GLOBAL");

        try (BufferedReader reader = Files.newBufferedReader(Path.of(archivoFuente));
             BufferedWriter writer = Files.newBufferedWriter(
                     Path.of(archivoTokens), StandardCharsets.UTF_8)) {
            Lexer lexerTokens = new Lexer(reader);

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

                long posicion = Parser.posicion(token.left, token.right);
                while (!activos.isEmpty() && activos.peek().fin < posicion) {
                    activos.pop();
                    rutas.pop();
                }

                Parser.Scope apertura = aperturas.get(posicion);
                if (apertura != null) {
                    String ruta = apertura.nombre;
                    if (!apertura.esFuncion) {
                        String prefijo = rutas.peek() + "/" + apertura.nombre;
                        int numero = contadorBloques.merge(prefijo, 1, Integer::sum);
                        ruta = prefijo + "#" + numero;
                    }
                    activos.push(apertura);
                    rutas.push(ruta);
                }

                String tablaSimbolos =
                        obtenerTablaSimbolos(nombreToken);


                writer.write(String.format(
                        "%-25s %-30s %-20s %-15s %-10d %-10d%n",
                        nombreToken,
                        lexema,
                        tablaSimbolos,
                        rutas.peek(),
                        token.left,
                        token.right
                ));


            } while (true);
        }
    }

    private static void imprimirArbol(Nodo nodo, int nivel) {
        String sangria = "  ".repeat(nivel);
        String texto = nodo.texto.isEmpty() ? "" : " " + nodo.texto;

        System.out.println(sangria + nodo.clase + texto);

        for (Nodo hijo : nodo.hijos) {
            imprimirArbol(hijo, nivel + 1);
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

        try (BufferedReader reader = Files.newBufferedReader(Path.of(archivoFuente))) {
            Parser parser = new Parser(new Lexer(reader));
            Nodo programa = analizarYGenerarTokens(archivoFuente, archivoTokens, parser);

            if (programa != null) {
                imprimirArbol(programa, 0);
            }

            if (!parser.errores.isEmpty()) {
                parser.errores.forEach(System.err::println);
                System.err.println("El archivo fuente contiene errores sintácticos.");
            } else if (programa == null) {
                System.err.println("El parser no devolvió el árbol del programa.");
            } else {
                List<ErrorCompilador> erroresSemanticos =
                        AnalizadorSemantico.analizar(programa);

                if (erroresSemanticos.isEmpty()) {
                    System.out.println("Programa sintáctica y semánticamente correcto.");
                    Path archivoIntermedio = Path.of("codigoIntermedio.txt");
                    List<String> instrucciones = GeneradorCodigoIntermedio.generar(programa);
                    Files.write(archivoIntermedio, instrucciones, StandardCharsets.UTF_8);
                    System.out.println("Código intermedio generado en " + archivoIntermedio.toAbsolutePath());
                } else {
                    erroresSemanticos.forEach(System.err::println);
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "Error durante el análisis: " + e.getMessage()
            );
        }
    }
}