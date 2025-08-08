interface Adder {
    int add(int a, int b);
}

interface Joiner {
    String add(String a, String b);
}

public class Test {
    static void execute(Adder adder) {
        System.out.println(adder.add(1, 2));
    }
    
    static void execute(Joiner joiner) {
        System.out.println(joiner.add("Hello", "World"));
    }
    
    public static void main(String[] args) {
        execute((a, b) -> a + b); // Ambiguous method call
        execute((String a, int b) -> a + b); // No match method

        execute((int a, int b) -> a + b); // OK
        execute((String a, String b) -> a + b); // OK
    }
}