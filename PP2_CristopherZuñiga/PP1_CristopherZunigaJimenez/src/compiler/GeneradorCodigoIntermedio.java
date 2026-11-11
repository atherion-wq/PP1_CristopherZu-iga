package compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GeneradorCodigoIntermedio {
    private final List<String> instrucciones = new ArrayList<>();
    private final Deque<Map<String, String>> scopes = new ArrayDeque<>();
    private final Map<String, Integer> declaracionesPorNombre = new HashMap<>();
    private final Map<String, String> capacidadesArreglos = new HashMap<>();
    private final Deque<String> salidasCiclos = new ArrayDeque<>();
    private int contadorTemporales;
    private int contadorEtiquetas;
    private int contadorInicioWhile;
    private int contadorSalidaWhile;
    private int contadorInicioFor;
    private int contadorSalidaFor;

    private GeneradorCodigoIntermedio() {
        scopes.push(new HashMap<>());
    }

    public static List<String> generar(Nodo programa) {
        GeneradorCodigoIntermedio generador = new GeneradorCodigoIntermedio();
        generador.generarSentencia(programa);
        return List.copyOf(generador.instrucciones);
    }

    private void generarSentencia(Nodo nodo) {
        switch (nodo.clase) {
            case PROGRAMA:
                generarSentencias(nodo.hijos);
                break;
            case PRINCIPAL:
                instrucciones.add("func principal()");
                generarSentencia(nodo.hijos.get(0));
                instrucciones.add("endfunc principal");
                break;
            case FUNCION:
                generarFuncion(nodo);
                break;
            case RETORNO:
                String valorRetorno = generarExpresion(nodo.hijos.get(0));
                instrucciones.add("return " + valorRetorno);
                break;
            case LLAMADA:
                instrucciones.add(generarLlamada(nodo));
                break;
            case BLOQUE:
                scopes.push(new HashMap<>());
                try {
                    generarSentencias(nodo.hijos);
                } finally {
                    scopes.pop();
                }
                break;
            case IF:
                generarIf(nodo);
                break;
            case WHILE:
                generarWhile(nodo);
                break;
            case FOR:
                generarFor(nodo);
                break;
            case BREAK:
                instrucciones.add("goto " + salidasCiclos.element());
                break;
            case DECLARACION:
                String nombre = nodo.hijos.get(1).texto;
                if (nodo.hijos.size() == 2) {
                    String tipo = nodo.hijos.get(0).texto;
                    instrucciones.add("var " + tipo + " " + declararNombre(nombre));
                } else {
                    String valor = generarExpresion(nodo.hijos.get(2));
                    instrucciones.add(declararNombre(nombre) + " = " + valor);
                }
                break;
            case DECLARACION_ARREGLO:
                generarDeclaracionArreglo(nodo);
                break;
            case ASIGNACION:
                generarAsignacion(nodo);
                break;
            case READ:
                String nombreLectura = buscarNombre(nodo.hijos.get(0).texto);
                instrucciones.add("read " + nombreLectura);
                break;
            case IDENTIFICADOR:
            case LITERAL_ENTERO:
            case LITERAL_FLOTANTE:
            case LITERAL_BOOLEANO:
            case LITERAL_CHAR:
            case LITERAL_CADENA:
            case ACCESO_ARREGLO:
            case OPERACION_BINARIA:
            case OPERACION_UNARIA:
                generarExpresion(nodo);
                break;
            case WRITE:
                String valorEscrito = generarExpresion(nodo.hijos.get(0));
                instrucciones.add("write " + valorEscrito);
                break;
            default:
                throw new IllegalStateException("Nodo inesperado como sentencia: " + nodo.clase);
        }
    }

    private void generarFuncion(Nodo nodo) {
        String nombre = nodo.hijos.get(1).texto;
        List<String> parametros = new ArrayList<>();

        scopes.push(new HashMap<>());
        try {
            for (Nodo parametro : nodo.hijos.get(2).hijos) {
                parametros.add(declararNombre(parametro.hijos.get(1).texto));
            }

            instrucciones.add("func " + nombre + "(" + String.join(", ", parametros) + ")");
            generarSentencias(nodo.hijos.get(3).hijos);
            instrucciones.add("endfunc " + nombre);
        } finally {
            scopes.pop();
        }
    }

    private String generarLlamada(Nodo nodo) {
        String nombre = nodo.hijos.get(0).texto;
        List<String> argumentos = generarValores(nodo.hijos.subList(1, nodo.hijos.size()));
        for (String argumento : argumentos) {
            instrucciones.add("param " + argumento);
        }
        return "call " + nombre + ", " + argumentos.size();
    }

    private void generarDeclaracionArreglo(Nodo nodo) {
        String tipo = nodo.hijos.get(0).texto;
        String capacidad = nodo.hijos.get(2).hijos.get(0).texto;
        List<String> valores = generarValores(nodo.hijos.get(3).hijos);
        String nombre = declararNombre(nodo.hijos.get(1).texto);

        capacidadesArreglos.put(nombre, capacidad);
        instrucciones.add("array " + tipo + " " + nombre + ", " + capacidad);
        escribirElementos(nombre, valores);
    }

    private void generarAsignacion(Nodo nodo) {
        Nodo destino = nodo.hijos.get(0);
        Nodo valor = nodo.hijos.get(1);
        if (destino.clase == Nodo.Clase.ACCESO_ARREGLO) {
            String posicion = generarPosicionArreglo(destino, true);
            instrucciones.add(posicion + " = " + generarExpresion(valor));
        } else {
            String nombre = buscarNombre(destino.texto);
            if (valor.clase == Nodo.Clase.LITERAL_ARREGLO) {
                escribirElementos(nombre, generarValores(valor.hijos));
            } else {
                instrucciones.add(nombre + " = " + generarExpresion(valor));
            }
        }
    }

    private List<String> generarValores(List<Nodo> expresiones) {
        List<String> valores = new ArrayList<>();
        for (int i = 0; i < expresiones.size(); i++) {
            Nodo expresion = expresiones.get(i);
            String valor = generarExpresion(expresion);
            if (expresion.clase == Nodo.Clase.IDENTIFICADOR && i + 1 < expresiones.size()) {
                valor = guardarEnTemporal(valor);
            }
            valores.add(valor);
        }
        return valores;
    }

    private void escribirElementos(String nombre, List<String> valores) {
        for (int i = 0; i < valores.size(); i++) {
            instrucciones.add(nombre + "[" + i + "] = " + valores.get(i));
        }
    }

    private String generarPosicionArreglo(Nodo acceso, boolean escritura) {
        String nombre = buscarNombre(acceso.hijos.get(0).texto);
        Nodo expresionIndice = acceso.hijos.get(1);
        String indice = generarExpresion(expresionIndice);
        if (escritura && expresionIndice.clase == Nodo.Clase.IDENTIFICADOR) {
            indice = guardarEnTemporal(indice);
        }

        String capacidad = capacidadesArreglos.get(nombre);
        String errorIndice = nuevaEtiqueta();
        String indiceValido = nuevaEtiqueta();

        instrucciones.add("if " + indice + " < 0 goto " + errorIndice);
        instrucciones.add("if " + indice + " >= " + capacidad + " goto " + errorIndice);
        instrucciones.add("goto " + indiceValido);
        instrucciones.add(errorIndice + ":");
        instrucciones.add("halt");
        instrucciones.add(indiceValido + ":");
        return nombre + "[" + indice + "]";
    }

    private void generarIf(Nodo nodo) {
        String fin = nuevaEtiqueta();
        generarRamaCondicional(nodo, fin, nodo.hijos.size() > 2);

        for (int i = 2; i < nodo.hijos.size(); i++) {
            Nodo rama = nodo.hijos.get(i);
            if (rama.clase == Nodo.Clase.ELSE) {
                generarSentencia(rama.hijos.get(0));
            } else {
                generarRamaCondicional(rama, fin, i + 1 < nodo.hijos.size());
            }
        }

        instrucciones.add(fin + ":");
    }

    private void generarRamaCondicional(Nodo rama, String fin, boolean tieneSiguiente) {
        String destinoFalso = fin;
        if (tieneSiguiente) {
            destinoFalso = nuevaEtiqueta();
        }

        String condicion = generarExpresion(rama.hijos.get(0));
        instrucciones.add("ifFalse " + condicion + " goto " + destinoFalso);
        generarSentencia(rama.hijos.get(1));

        if (tieneSiguiente) {
            instrucciones.add("goto " + fin);
            instrucciones.add(destinoFalso + ":");
        }
    }

    private void generarWhile(Nodo nodo) {
        String inicioWhile = nuevaEtiquetaInicioWhile();
        String salidaWhile = nuevaEtiquetaSalidaWhile();

        instrucciones.add(inicioWhile + ":");
        String condicion = generarExpresion(nodo.hijos.get(0));
        instrucciones.add("ifFalse " + condicion + " goto " + salidaWhile);

        salidasCiclos.push(salidaWhile);
        try {
            generarSentencia(nodo.hijos.get(1));
        } finally {
            salidasCiclos.pop();
        }

        instrucciones.add("goto " + inicioWhile);
        instrucciones.add(salidaWhile + ":");
    }

    private void generarFor(Nodo nodo) {
        scopes.push(new HashMap<>());
        try {
            Nodo inicializacion = nodo.hijos.get(0);
            if (inicializacion.clase == Nodo.Clase.IDENTIFICADOR) {
                generarExpresion(inicializacion);
            } else {
                generarSentencia(inicializacion);
            }

            String inicioFor = nuevaEtiquetaInicioFor();
            String salidaFor = nuevaEtiquetaSalidaFor();
            instrucciones.add(inicioFor + ":");
            String condicion = generarExpresion(nodo.hijos.get(1));
            instrucciones.add("ifFalse " + condicion + " goto " + salidaFor);

            salidasCiclos.push(salidaFor);
            try {
                generarSentencias(nodo.hijos.get(3).hijos);
            } finally {
                salidasCiclos.pop();
            }

            generarExpresion(nodo.hijos.get(2));
            instrucciones.add("goto " + inicioFor);
            instrucciones.add(salidaFor + ":");
        } finally {
            scopes.pop();
        }
    }

    private String nuevaEtiqueta() {
        return "L" + ++contadorEtiquetas;
    }

    private String nuevaEtiquetaInicioWhile() {
        return "inicioWhile" + ++contadorInicioWhile;
    }

    private String nuevaEtiquetaSalidaWhile() {
        return "salidaWhile" + ++contadorSalidaWhile;
    }

    private String nuevaEtiquetaInicioFor() {
        return "inicioFor" + ++contadorInicioFor;
    }

    private String nuevaEtiquetaSalidaFor() {
        return "salidaFor" + ++contadorSalidaFor;
    }

    private void generarSentencias(List<Nodo> sentencias) {
        for (Nodo sentencia : sentencias) {
            generarSentencia(sentencia);
        }
    }

    private String declararNombre(String nombre) {
        int numeroDeclaracion = declaracionesPorNombre.merge(nombre, 1, Integer::sum);
        String intermedio = nombreIntermedio(nombre);
        if (numeroDeclaracion > 1) {
            intermedio += "." + numeroDeclaracion;
        }

        scopes.peek().put(nombre, intermedio);
        return intermedio;
    }

    private String buscarNombre(String nombre) {
        for (Map<String, String> scope : scopes) {
            String intermedio = scope.get(nombre);
            if (intermedio != null) {
                return intermedio;
            }
        }
        throw new IllegalStateException("Falta el nombre intermedio de la variable: " + nombre);
    }

    private String nombreIntermedio(String nombre) {
        if (nombre.matches("t[0-9]+")) {
            return "var." + nombre;
        }

        return nombre;
    }

    private String generarExpresion(Nodo expresion) {
        switch (expresion.clase) {
            case IDENTIFICADOR:
                return buscarNombre(expresion.texto);
            case LITERAL_ENTERO:
            case LITERAL_FLOTANTE:
            case LITERAL_BOOLEANO:
            case LITERAL_CHAR:
            case LITERAL_CADENA:
                return expresion.texto;
            case ACCESO_ARREGLO:
                return guardarEnTemporal(generarPosicionArreglo(expresion, false));
            case LLAMADA:
                return guardarEnTemporal(generarLlamada(expresion));
            case OPERACION_BINARIA:
                return generarBinaria(expresion);
            case OPERACION_UNARIA:
                return generarUnaria(expresion);
            default:
                throw new IllegalStateException("Nodo inesperado como expresión: " + expresion.clase);
        }
    }

    private String generarBinaria(Nodo expresion) {
        Nodo operandoIzquierdo = expresion.hijos.get(0);
        Nodo operandoDerecho = expresion.hijos.get(1);
        String izquierdo = generarExpresion(operandoIzquierdo);
        if (operandoIzquierdo.clase == Nodo.Clase.IDENTIFICADOR
                && modificaVariable(operandoDerecho, operandoIzquierdo.texto)) {
            izquierdo = guardarEnTemporal(izquierdo);
        }
        String derecho = generarExpresion(operandoDerecho);
        return guardarEnTemporal(izquierdo + " " + expresion.texto + " " + derecho);
    }

    private String generarUnaria(Nodo expresion) {
        String operando = generarExpresion(expresion.hijos.get(0));

        switch (expresion.texto) {
            case "-":
                return "-" + operando;
            case "Σ":
                return guardarEnTemporal("Σ " + operando);
            case "++":
                String antesDeIncrementar = guardarEnTemporal(operando);
                instrucciones.add(operando + " = " + antesDeIncrementar + " + 1");
                return antesDeIncrementar;
            case "--":
                String antesDeDecrementar = guardarEnTemporal(operando);
                instrucciones.add(operando + " = " + antesDeDecrementar + " - 1");
                return antesDeDecrementar;
            default:
                throw new IllegalStateException("Operador unario inesperado: " + expresion.texto);
        }
    }

    private String guardarEnTemporal(String valor) {
        String temporal = "t" + ++contadorTemporales;
        instrucciones.add(temporal + " = " + valor);
        return temporal;
    }

    private boolean modificaVariable(Nodo expresion, String nombre) {
        if (expresion.clase == Nodo.Clase.LLAMADA) {
            return true;
        }

        if (expresion.clase == Nodo.Clase.OPERACION_UNARIA
                && (expresion.texto.equals("++") || expresion.texto.equals("--"))) {
            return expresion.hijos.get(0).texto.equals(nombre);
        }

        for (Nodo hijo : expresion.hijos) {
            if (modificaVariable(hijo, nombre)) {
                return true;
            }
        }
        return false;
    }

}
