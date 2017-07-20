package com.github.t1.nginx;

import lombok.*;

import java.io.*;
import java.util.function.*;

import static java.lang.Character.*;

class Tokenizer {
    private final Reader reader;

    Tokenizer(Reader reader) { this.reader = reader; }

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

    static class Visitor {
        public Visitor visitWhitespace(String whitespace) { return this; }

        public Visitor visitToken(String token) { return this; }

        public Visitor startBlock() { return this; }

        public Visitor endBlock() { return this; }
    }

    @AllArgsConstructor
    static class ValueVisitor extends Visitor {
        private Visitor next;
        private Consumer<String> consumer;

        @Override public Visitor visitToken(String token) {
            assert token.endsWith(";");
            consumer.accept(token.substring(0, token.length() - 1));
            return next;
        }
    }

    static class NamedBlockNameVisitor extends Visitor {
        private NamedBlockVisitor next;

        NamedBlockNameVisitor(NamedBlockVisitor next) { this.next = next; }

        @Override public Visitor visitToken(String token) {
            next.setName(token);
            return this;
        }

        @Override public Visitor startBlock() { return next; }

        @Override public Visitor endBlock() { throw new UnsupportedOperationException("should never get here"); }
    }

    static abstract class BlockVisitor extends Visitor {
        private final StringBuilder before = new StringBuilder();
        private final StringBuilder after = new StringBuilder();
        private StringBuilder current = before;
        private final Visitor next;

        private BlockVisitor(Visitor next) { this.next = next; }

        void append(String string) { current.append(string); }

        void toAfter() { current = after; }

        String before() { return before.toString().trim(); }

        String after() { return after.toString().trim(); }

        Visitor next() { return next; }
    }

    static abstract class NamedBlockVisitor extends BlockVisitor {
        NamedBlockVisitor(Visitor next) { super(next); }

        abstract void setName(String name);
    }
}
