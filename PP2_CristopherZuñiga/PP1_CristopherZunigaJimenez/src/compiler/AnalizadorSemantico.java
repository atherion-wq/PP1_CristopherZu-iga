package compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class AnalizadorSemantico {
    private final Deque<Map<String, Variable>> scopes = new ArrayDeque<>();
    private final Map<String, Nodo> funciones = new HashMap<>();
    private final List<ErrorCompilador> errores = new ArrayList<>();

    private static final class Variable {
        final String tipo;
        final Integer capacidad;

        Variable(String tipo, Integer capacidad) {
            this.tipo = tipo;
            this.capacidad = capacidad;
        }
    }

    private int profundidadCiclos;
    private String tipoRetornoActual;

    private AnalizadorSemantico() {
    }

    public static List<ErrorCompilador> analizar(Nodo programa) {
        AnalizadorSemantico analizador = new AnalizadorSemantico();
        analizador.registrarFunciones(programa);
        analizador.visitar(programa);
        return List.copyOf(analizador.errores);
    }

    private void visitar(Nodo nodo) {
        switch (nodo.clase) {
            case PROGRAMA:
            case BLOQUE:
                scopes.push(new HashMap<>());
                for (Nodo hijo : nodo.hijos) {
                    visitar(hijo);
                }
                scopes.pop();
                break;
            case FUNCION:
                visitarFuncion(nodo);
                break;
            case PRINCIPAL:
            case ELSE:
                for (Nodo hijo : nodo.hijos) {
                    visitar(hijo);
                }
                break;
            case IF:
            case ELIF:
                Nodo condicion = nodo.hijos.get(0);
                comprobarTipo("bool", condicion);

                for (int i = 1; i < nodo.hijos.size(); i++) {
                    visitar(nodo.hijos.get(i));
                }
                break;
            case WHILE:
                comprobarTipo("bool", nodo.hijos.get(0));
                visitarCuerpoCiclo(List.of(nodo.hijos.get(1)));
                break;
            case BREAK:
                if (profundidadCiclos == 0) {
                    error(nodo, "break solo se permite dentro de un ciclo");
                }
                break;
            case FOR:
                visitarFor(nodo);
                break;
            case DECLARACION:
                visitarDeclaracion(nodo);
                break;
            case DECLARACION_ARREGLO:
                visitarDeclaracionArreglo(nodo);
                break;
            case ASIGNACION:
                Nodo destino = nodo.hijos.get(0);
                Nodo valor = nodo.hijos.get(1);
                if (valor.clase == Nodo.Clase.LITERAL_ARREGLO) {
                    comprobarAsignacionArreglo(destino, valor);
                } else {
                    comprobarTipo(tipoDeExpresion(destino), valor);
                }
                break;
            case READ:
                comprobarEntradaSalida(nodo, "read", List.of("int", "float", "char"));
                break;
            case WRITE:
                comprobarEntradaSalida(nodo, "write", List.of("string", "int", "float", "bool"));
                break;
            case RETORNO:
                if (tipoRetornoActual == null) {
                    error(nodo, "return con valor requiere una función con tipo de retorno");
                }
                comprobarTipo(tipoRetornoActual, nodo.hijos.get(0));
                break;
            case IDENTIFICADOR:
            case OPERACION_BINARIA:
            case OPERACION_UNARIA:
            case ACCESO_ARREGLO:
            case LLAMADA:
                tipoDeExpresion(nodo);
                break;
            default:
                break;
        }
    }

    private void registrarFunciones(Nodo programa) {
        for (Nodo hijo : programa.hijos) {
            if (hijo.clase == Nodo.Clase.FUNCION) {
                Nodo nombre = hijo.hijos.get(1);
                if (funciones.putIfAbsent(nombre.texto, hijo) != null) {
                    error(nombre, "La función ya fue declarada");
                }
            }
        }
    }

    private void visitarFuncion(Nodo funcion) {
        Nodo parametros = funcion.hijos.get(2);
        Nodo cuerpo = funcion.hijos.get(3);

        String tipoRetornoAnterior = tipoRetornoActual;
        scopes.push(new HashMap<>());
        tipoRetornoActual = funcion.hijos.get(0).texto;
        try {
            for (Nodo parametro : parametros.hijos) {
                if (parametro.clase == Nodo.Clase.PARAMETRO) {
                    registrarVariable(parametro.hijos.get(1), parametro.hijos.get(0).texto);
                }
            }

            for (Nodo sentencia : cuerpo.hijos) {
                visitar(sentencia);
            }
            if (!garantizaRetorno(cuerpo)) {
                error(funcion.hijos.get(1), "La función puede terminar sin devolver un valor");
            }
        } finally {
            tipoRetornoActual = tipoRetornoAnterior;
            scopes.pop();
        }
    }

    private boolean garantizaRetorno(Nodo nodo) {
        switch (nodo.clase) {
            case RETORNO:
                return true;
            case BLOQUE:
                for (Nodo sentencia : nodo.hijos) {
                    if (garantizaRetorno(sentencia)) {
                        return true;
                    }
                }
                return false;
            case IF:
                Nodo ultimaRama = nodo.hijos.get(nodo.hijos.size() - 1);
                if (ultimaRama.clase != Nodo.Clase.ELSE) {
                    return false;
                }
                for (int i = 1; i < nodo.hijos.size(); i++) {
                    if (!garantizaRetorno(nodo.hijos.get(i))) {
                        return false;
                    }
                }
                return true;
            case ELIF:
                return garantizaRetorno(nodo.hijos.get(1));
            case ELSE:
                return garantizaRetorno(nodo.hijos.get(0));
            default:
                return false;
        }
    }

    private void visitarFor(Nodo nodo) {
        scopes.push(new HashMap<>());
        try {
            Nodo inicializacion = nodo.hijos.get(0);
            if (inicializacion.clase == Nodo.Clase.IDENTIFICADOR) {
                buscarTipo(inicializacion);
            } else {
                visitar(inicializacion);
            }

            comprobarTipo("bool", nodo.hijos.get(1));

            Nodo actualizacion = nodo.hijos.get(2);
            String tipoActualizacion = tipoDeExpresion(actualizacion);
            if (tipoActualizacion != null && !esNumerico(tipoActualizacion)) {
                error(actualizacion, "La actualización del for debe ser numérica");
            }
            visitarCuerpoCiclo(nodo.hijos.get(3).hijos);
        } finally {
            scopes.pop();
        }
    }

    private void visitarCuerpoCiclo(List<Nodo> cuerpo) {
        profundidadCiclos++;
        try {
            for (Nodo nodo : cuerpo) {
                visitar(nodo);
            }
        } finally {
            profundidadCiclos--;
        }
    }

    private void visitarDeclaracion(Nodo declaracion) {
        String tipoDeclarado = declaracion.hijos.get(0).texto;
        Nodo nombre = declaracion.hijos.get(1);

        if (declaracion.hijos.size() == 3) {
            comprobarTipo(tipoDeclarado, declaracion.hijos.get(2));
        }

        registrarVariable(nombre, tipoDeclarado);
    }

    private void visitarDeclaracionArreglo(Nodo declaracion) {
        Nodo tipo = declaracion.hijos.get(0);
        Nodo nombre = declaracion.hijos.get(1);
        Nodo tamano = declaracion.hijos.get(2).hijos.get(0);
        Nodo inicializador = declaracion.hijos.get(3);

        int capacidad = leerCapacidad(tamano);
        boolean tipoValido = esNumerico(tipo.texto);
        if (!tipoValido) {
            error(tipo, "Un arreglo solo admite elementos int o float");
        }

        comprobarLiteralArreglo(nombre, inicializador, tipoValido ? tipo.texto : null, capacidad);
        registrarVariable(nombre, new Variable(tipo.texto, capacidad));
    }

    private void comprobarAsignacionArreglo(Nodo destino, Nodo literal) {
        Variable variable = destino.clase == Nodo.Clase.IDENTIFICADOR
                ? buscarVariable(destino) : null;

        if (variable == null || variable.capacidad == null) {
            if (variable != null) {
                error(destino, "El destino de un literal de arreglo debe ser un arreglo");
            }
            comprobarLiteralArreglo(destino, literal, null, 0);
            return;
        }

        String tipoElemento = esNumerico(variable.tipo) ? variable.tipo : null;
        comprobarLiteralArreglo(destino, literal, tipoElemento, variable.capacidad);
    }

    private void comprobarLiteralArreglo(Nodo nombre, Nodo literal, String tipoElemento, int capacidad) {
        if (capacidad > 0 && literal.hijos.size() > capacidad) {
            error(nombre, "El literal supera la capacidad del arreglo (" + capacidad + ")");
        }

        for (Nodo elemento : literal.hijos) {
            comprobarTipo(tipoElemento, elemento);
        }
    }

    private int leerCapacidad(Nodo tamano) {
        try {
            int capacidad = Integer.parseInt(tamano.texto);
            if (capacidad > 0) {
                return capacidad;
            }
            error(tamano, "El tamaño del arreglo debe ser positivo");
        } catch (NumberFormatException e) {
            error(tamano, "El tamaño del arreglo supera el rango de int");
        }
        return 0;
    }

    private void registrarVariable(Nodo nombre, String tipoDeclarado) {
        registrarVariable(nombre, new Variable(tipoDeclarado, null));
    }

    private void registrarVariable(Nodo nombre, Variable variable) {
        if (encontrarVariableVisible(nombre.texto) != null) {
            error(nombre, "La variable ya fue declarada en un scope visible");
            return;
        }
        scopes.peek().put(nombre.texto, variable);
    }

    private void comprobarEntradaSalida(Nodo nodo, String operacion, List<String> tiposPermitidos) {
        Nodo argumento = nodo.hijos.get(0);
        String tipo = tipoDeExpresion(argumento);
        if (tipo != null && !tiposPermitidos.contains(tipo)) {
            error(argumento, operacion + " no admite el tipo " + tipo);
        }
    }

    private void comprobarTipo(String esperado, Nodo expresion) {
        String actual = tipoDeExpresion(expresion);
        if (esperado != null && actual != null && !esperado.equals(actual)) {
            error(expresion, "Se esperaba " + esperado + " y se obtuvo " + actual);
        }
    }

    private String tipoDeExpresion(Nodo expresion) {
        switch (expresion.clase) {
            case LITERAL_ENTERO: return "int";
            case LITERAL_FLOTANTE: return "float";
            case LITERAL_BOOLEANO: return "bool";
            case LITERAL_CHAR: return "char";
            case LITERAL_CADENA: return "string";
            case IDENTIFICADOR: return buscarTipo(expresion);
            case ACCESO_ARREGLO: return tipoDeAccesoArreglo(expresion);
            case LLAMADA: return comprobarLlamada(expresion);
            case OPERACION_BINARIA:
                String izquierdo = tipoDeExpresion(expresion.hijos.get(0));
                String derecho = tipoDeExpresion(expresion.hijos.get(1));
                return comprobarOperacionBinaria(expresion, izquierdo, derecho);
            case OPERACION_UNARIA:
                String operando = tipoDeExpresion(expresion.hijos.get(0));
                return comprobarOperacionUnaria(expresion, operando);
            default: return null;
        }
    }

    private String tipoDeAccesoArreglo(Nodo acceso) {
        Nodo nombre = acceso.hijos.get(0);
        Nodo indice = acceso.hijos.get(1);
        Variable variable = buscarVariable(nombre);
        String tipoIndice = tipoDeExpresion(indice);

        if (variable != null && variable.capacidad == null) {
            error(nombre, "La variable no es un arreglo");
        }
        if (tipoIndice != null && !tipoIndice.equals("int")) {
            error(indice, "El índice del arreglo debe ser int");
        }
        if (variable == null || variable.capacidad == null || variable.capacidad <= 0
                || !"int".equals(tipoIndice)) {
            return null;
        }
        if (!indiceLiteralEnRango(indice, variable.capacidad)) {
            error(indice, "Índice fuera de rango para un arreglo de capacidad " + variable.capacidad);
            return null;
        }
        return variable.tipo;
    }

    private boolean indiceLiteralEnRango(Nodo indice, int capacidad) {
        String texto;
        if (indice.clase == Nodo.Clase.LITERAL_ENTERO) {
            texto = indice.texto;
        } else if (indice.clase == Nodo.Clase.OPERACION_UNARIA && indice.texto.equals("-")
                && indice.hijos.get(0).clase == Nodo.Clase.LITERAL_ENTERO) {
            texto = "-" + indice.hijos.get(0).texto;
        } else {
            return true;
        }

        try {
            int posicion = Integer.parseInt(texto);
            return posicion >= 0 && posicion < capacidad;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String comprobarLlamada(Nodo llamada) {
        int erroresAntes = errores.size();
        Nodo nombre = llamada.hijos.get(0);
        Nodo funcion = funciones.get(nombre.texto);
        int cantidadArgumentos = llamada.hijos.size() - 1;
        List<Nodo> parametros = List.of();

        if (funcion == null) {
            error(nombre, "La función no está declarada");
        } else {
            parametros = funcion.hijos.get(2).hijos;
            if (cantidadArgumentos != parametros.size()) {
                error(nombre, "Se esperaban " + parametros.size()
                        + " argumentos y se recibieron " + cantidadArgumentos);
            }
        }

        for (int i = 0; i < cantidadArgumentos; i++) {
            Nodo argumento = llamada.hijos.get(i + 1);
            if (i < parametros.size() && parametros.get(i).clase == Nodo.Clase.PARAMETRO) {
                comprobarTipo(parametros.get(i).hijos.get(0).texto, argumento);
            } else {
                tipoDeExpresion(argumento);
            }
        }

        if (funcion == null || errores.size() > erroresAntes) {
            return null;
        }
        return funcion.hijos.get(0).texto;
    }

    private String comprobarOperacionBinaria(Nodo expresion, String izquierdo, String derecho) {
        if (izquierdo == null || derecho == null) {
            return null;
        }

        switch (expresion.texto) {
            case "+":
            case "-":
            case "*":
            case "/":
                if (esNumerico(izquierdo) && esNumerico(derecho)) {
                    if (expresion.texto.equals("/")) {
                        return "float";
                    }
                    return izquierdo.equals("float") || derecho.equals("float") ? "float" : "int";
                }
                error(expresion, "El operador " + expresion.texto + " requiere operandos numéricos");
                break;
            case "//":
                if (esNumerico(izquierdo) && esNumerico(derecho)) {
                    return "int";
                }
                error(expresion, "El operador // requiere operandos numéricos");
                break;
            case "mod":
            case "pot":
                if (izquierdo.equals("int") && derecho.equals("int")) {
                    return "int";
                }
                error(expresion, "El operador " + expresion.texto + " requiere operandos int");
                break;
            case "<":
            case "<=":
            case ">":
            case ">=":
                if (esNumerico(izquierdo) && esNumerico(derecho)) {
                    return "bool";
                }
                error(expresion, "El operador " + expresion.texto + " requiere operandos numéricos");
                break;
            case "==":
            case "!=":
                if ((esNumerico(izquierdo) && esNumerico(derecho))
                        || (izquierdo.equals(derecho) && (izquierdo.equals("bool") || izquierdo.equals("char")))) {
                    return "bool";
                }
                error(expresion, "El operador " + expresion.texto
                        + " no puede comparar " + izquierdo + " con " + derecho);
                break;
            case "λ":
            case "θ":
                if (izquierdo.equals("bool") && derecho.equals("bool")) {
                    return "bool";
                }
                error(expresion, "El operador " + expresion.texto + " requiere operandos bool");
                break;
            default:
                error(expresion, "Operador binario desconocido");
                break;
        }
        return null;
    }

    private String comprobarOperacionUnaria(Nodo expresion, String tipo) {
        if (tipo == null) {
            return null;
        }

        switch (expresion.texto) {
            case "-":
            case "++":
            case "--":
                
                if (esNumerico(tipo)) {
                    return tipo;
                }
                error(expresion, "El operador " + expresion.texto + " requiere un operando numérico");
                break;
            case "Σ":
                if (tipo.equals("bool")) {
                    return "bool";
                }
                error(expresion, "El operador Σ requiere un operando bool");
                break;
            default:
                error(expresion, "Operador unario desconocido");
                break;
        }
        return null;
    }

    private boolean esNumerico(String tipo) {
        return tipo.equals("int") || tipo.equals("float");
    }

    private String buscarTipo(Nodo nombre) {
        Variable variable = buscarVariable(nombre);
        if (variable == null) {
            return null;
        }
        return variable.capacidad == null ? variable.tipo : variable.tipo + "[]";
    }

    private Variable buscarVariable(Nodo nombre) {
        Variable variable = encontrarVariableVisible(nombre.texto);
        if (variable == null) {
            error(nombre, "La variable no está declarada en un scope visible");
        }
        return variable;
    }

    private Variable encontrarVariableVisible(String nombre) {
        for (Map<String, Variable> scope : scopes) {
            Variable variable = scope.get(nombre);
            if (variable != null) {
                return variable;
            }
        }
        return null;
    }

    private void error(Nodo nodo, String descripcion) {
        errores.add(new ErrorCompilador("Semántico", nodo.texto, descripcion,
                nodo.linea, nodo.columna));
    }
}
