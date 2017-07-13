package com.github.t1.nginx;

import lombok.SneakyThrows;

import java.io.*;
import java.util.function.Predicate;

import static java.lang.Character.*;

class Tokenizer {
    private final Reader reader;

    public Tokenizer(Reader reader) { this.reader = reader; }

    public Tokenizer(StringBuilder text) { this(text.toString()); }

    public Tokenizer(String text) { this(new StringReader(text)); }

    @SneakyThrows(IOException.class)
    void accept(Visitor visitor) {
        while (true) {
            String whitespace = readAll(Character::isWhitespace);
            if (whitespace == null) {
                if (readIf('{')) {
                    visitor = visitor.startBlock();
                } else if (readIf('}')) {
                    visitor = visitor.endBlock();
                } else {
                    String token = readAll(codePoint -> !isWhitespace(codePoint));
                    if (token == null)
                        return;
                    visitor = visitor.visitToken(token);
                }
            } else {
                visitor = visitor.visitWhitespace(whitespace);
            }
        }
    }

    private String readAll(Predicate<Integer> predicate) throws IOException {
        Predicate<Integer> inner = c -> c >= 0 && predicate.test(c);
        if (!inner.test(peek()))
            return null;

        StringBuilder out = new StringBuilder();
        do {
            out.appendCodePoint(reader.read());
        } while (inner.test(peek()));
        return out.toString();
    }

    private int peek() throws IOException {
        reader.mark(1);
        int c = reader.read();
        reader.reset();
        return c;
    }

    private boolean readIf(int expected) throws IOException {
        reader.mark(1);
        int actual = reader.read();
        if (expected == actual)
            return true;
        reader.reset();
        return false;
    }

    public static class Visitor {
        public Visitor visitWhitespace(String whitespace) { return this; }

        public Visitor visitToken(String token) { return this; }

        public Visitor startBlock() { return this; }

        public Visitor endBlock() { return this; }
    }
}
