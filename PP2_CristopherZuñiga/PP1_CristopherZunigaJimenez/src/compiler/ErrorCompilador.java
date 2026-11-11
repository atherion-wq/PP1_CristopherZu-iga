package compiler;

public class ErrorCompilador {
    public final String tipo, lexema, descripcion;
    public final int linea, columna;

    public ErrorCompilador(String tipo, String lexema, String descripcion, int linea, int columna) {
        this.tipo = tipo; this.lexema = lexema; this.descripcion = descripcion;
        this.linea = linea; this.columna = columna;
    }

    @Override
    public String toString() {
        return tipo + " | línea " + linea + ", col " + columna + " | '" + lexema + "' | " + descripcion;
    }




}
