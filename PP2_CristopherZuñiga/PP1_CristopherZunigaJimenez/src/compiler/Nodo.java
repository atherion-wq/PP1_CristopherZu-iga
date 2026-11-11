package compiler;

import java.util.List;
import java.util.Objects;

public final class Nodo {
    public enum Clase {
        PROGRAMA,
        BLOQUE,
        FUNCION,
        PRINCIPAL,
        PARAMETROS,
        PARAMETRO,
        LLAMADA,
        RETORNO,

        DECLARACION,
        DECLARACION_ARREGLO,
        TAMANO_ARREGLO,
        LITERAL_ARREGLO,
        ACCESO_ARREGLO,
        ASIGNACION,

        IF,
        ELIF,
        ELSE,
        WHILE,
        FOR,
        BREAK,
        READ,
        WRITE,

        OPERACION_BINARIA,
        OPERACION_UNARIA,
        TIPO,
        IDENTIFICADOR,
        LITERAL_ENTERO,
        LITERAL_FLOTANTE,
        LITERAL_BOOLEANO,
        LITERAL_CHAR,
        LITERAL_CADENA,
        ERROR
    }

    public final Clase clase;
    public final String texto;
    public final int linea;
    public final int columna;
    public final List<Nodo> hijos;

    public Nodo(Clase clase, String texto, int linea, int columna, List<Nodo> hijos) {
        this.clase = Objects.requireNonNull(clase);
        this.texto = Objects.requireNonNull(texto);
        this.linea = linea;
        this.columna = columna;
        this.hijos = List.copyOf(hijos);
    }
}
