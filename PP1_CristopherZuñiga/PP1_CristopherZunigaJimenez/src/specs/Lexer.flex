package compiler;

import java_cup.runtime.Symbol;

%%

%public
%class Lexer
%unicode
%cup
%line
%column
%eofval{
    return new Symbol(sym.EOF, yyline + 1, yycolumn + 1);
%eofval}

%state COMENTARIO_MULTILINEA

%{
    private int lineaInicioComentario = -1;

    private Symbol symbol(int type) {
        return new Symbol(
                type,
                yyline + 1,
                yycolumn + 1,
                yytext()
        );
    }
%}



LETRA = [a-zA-Z]

DIGITO = [0-9]

DIGITO_NO_CERO = [1-9]

IDENTIFICADOR = {LETRA}({LETRA}|{DIGITO}|_)* | _({LETRA}|{DIGITO}|_)+

CERO = 0

ENTERO_POSITIVO = {DIGITO_NO_CERO}{DIGITO}*

ENTERO = {CERO}|{ENTERO_POSITIVO}

FLOTANTE = {ENTERO}\.{DIGITO}+

CARACTER = \'[^\'\r\n]\'

CADENA = \"[^\"\r\n]*\"

ESPACIO = [ \t\f\r\n]+

%%



/* ------------  Palabras reservadas ---------------- */

<YYINITIAL>"void" {
    return symbol(sym.VOID);
}

<YYINITIAL>"main" {
    return symbol(sym.MAIN);
}

<YYINITIAL>"val" {
    return symbol(sym.VAL);
}

<YYINITIAL>"return" {
    return symbol(sym.RETURN);
}

<YYINITIAL>"if" {
    return symbol(sym.IF);
}

<YYINITIAL>"elif" {
    return symbol(sym.ELIF);
}

<YYINITIAL>"else" {
    return symbol(sym.ELSE);
}

<YYINITIAL>"while" {
    return symbol(sym.WHILE);
}

<YYINITIAL>"for" {
    return symbol(sym.FOR);
}

<YYINITIAL>"break" {
    return symbol(sym.BREAK);
}


<YYINITIAL>"int" {
    return symbol(sym.INT);
}

<YYINITIAL>"float" {
    return symbol(sym.FLOAT);
}

<YYINITIAL>"bool" {
    return symbol(sym.BOOL);
}

<YYINITIAL>"char" {
    return symbol(sym.CHAR);
}

<YYINITIAL>"string" {
    return symbol(sym.STRING);
}


<YYINITIAL>"write" {
    return symbol(sym.WRITE);
}

<YYINITIAL>"read" {
    return symbol(sym.READ);
}

/* ------------  Booleanos ---------------- */

<YYINITIAL>"true" {
    return symbol(sym.TRUE);
}

<YYINITIAL>"false" {
    return symbol(sym.FALSE);
}

/* ------------  Comentarios  ---------------- */



<YYINITIAL>"|"[^\r\n]* {
    /* comentario ignorado */
}


<YYINITIAL>"¡" {

    lineaInicioComentario = yyline + 1;

    yybegin(COMENTARIO_MULTILINEA);
}


/* ------------  Delimitadores y Simbolos  ---------------- */


<YYINITIAL>"є:" {
    return symbol(sym.PARENTESIS_ABRE);
}

<YYINITIAL>":э" {
    return symbol(sym.PARENTESIS_CIERRA);
}

<YYINITIAL>"¿:" {
    return symbol(sym.BLOQUE_ABRE);
}

<YYINITIAL>":?" {
    return symbol(sym.BLOQUE_CIERRA);
}


<YYINITIAL>"ʃ:" {
    return symbol(sym.CORCHETE_ABRE);
}

<YYINITIAL>":ʅ" {
    return symbol(sym.CORCHETE_CIERRA);
}

<YYINITIAL>"Ͱ" {
    return symbol(sym.ASIGNACION);
}

<YYINITIAL>"»" {
    return symbol(sym.DELIMITADOR);
}

<YYINITIAL>"," {
    return symbol(sym.COMA);
}


/* ------------  Operadores Rel  ---------------- */

<YYINITIAL>"<=" {
    return symbol(sym.MENOR_IGUAL);
}

<YYINITIAL>">=" {
    return symbol(sym.MAYOR_IGUAL);
}

<YYINITIAL>"==" {
    return symbol(sym.IGUAL);
}

<YYINITIAL>"!=" {
    return symbol(sym.DIFERENTE);
}


<YYINITIAL>"<" {
    return symbol(sym.MENOR);
}

<YYINITIAL>">" {
    return symbol(sym.MAYOR);
}


/* ------------  Operadores Logicos  ---------------- */

<YYINITIAL>"λ" {
    return symbol(sym.CONJUNCION);
}

<YYINITIAL>"θ" {
    return symbol(sym.DISYUNCION);
}

<YYINITIAL>"Σ" {
    return symbol(sym.NEGACION);
}


/* ------------  Operadores Arit  ---------------- */

<YYINITIAL>"++" {
    return symbol(sym.INCREMENTO);
}

<YYINITIAL>"--" {
    return symbol(sym.DECREMENTO);
}

<YYINITIAL>"//" {
    return symbol(sym.DIVISION_ENTERA);
}


<YYINITIAL>"+" {
    return symbol(sym.MAS);
}

<YYINITIAL>"-" {
    return symbol(sym.MENOS);
}

<YYINITIAL>"*" {
    return symbol(sym.MULTIPLICACION);
}

<YYINITIAL>"/" {
    return symbol(sym.DIVISION);
}

<YYINITIAL>"%" {
    return symbol(sym.MODULO);
}

<YYINITIAL>"^" {
    return symbol(sym.POTENCIA);
}


/* ------------  Literales  ---------------- */

<YYINITIAL>{FLOTANTE} {
    return symbol(sym.LITERAL_FLOAT);
}

<YYINITIAL>{CERO} {
    return symbol(sym.CERO);
}

<YYINITIAL>{ENTERO_POSITIVO} {
    return symbol(sym.ENTERO_POSITIVO);
}

<YYINITIAL>{CARACTER} {
    return symbol(sym.LITERAL_CHAR);
}

<YYINITIAL>{CADENA} {
    return symbol(sym.LITERAL_STRING);
}

/* ------------  ID  ---------------- */

<YYINITIAL>{IDENTIFICADOR} {
    return symbol(sym.IDENTIFICADOR);
}


/* ------------  BlankSpace  ---------------- */

<YYINITIAL>{ESPACIO} {
    /* ignorar */
}

/* ------------  Error  ---------------- */

<YYINITIAL>. {

    System.err.println(
            "Error léxico en línea "
            + (yyline + 1)
            + ", columna "
            + (yycolumn + 1)
            + ": carácter no reconocido '"
            + yytext()
            + "'"
    );
}

/* ------------  Comentarios  ---------------- */

<COMENTARIO_MULTILINEA>"!" {

    yybegin(YYINITIAL);
}

<COMENTARIO_MULTILINEA>[^!]+ {
    /* ignorar contenido */
}


