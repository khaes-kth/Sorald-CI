public class LiteralIntOperand {
    public static void literalIntAsLeftOperand() {
        int a = 22;
        long longVal = 1000l + a; // Noncompliant
        float floatVal = 1000f + a; // Noncompliant
        double doubleVal = 1000d + a; // Noncompliant
        double doubleDiv = 1000d / a; // Noncompliant
    }

    public static void literalIntAsRightOperand() {
        int a = 22;
        long longVal = a + 1000l ; // Noncompliant
        float floatVal = a + 1000f ; // Noncompliant
        double doubleVal = a + 1000d ; // Noncompliant
        double doubleDiv = a / 1000d ; // Noncompliant
    }
}