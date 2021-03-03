import java.util.Arrays;
// the type of this method cannot be resolved
import static some.unavailable.pkg.someMethod;

public class NOCOMPILE_UnresolvableType {
    public static void main(String[] args) {
        int[] arr = new int[]{1,2,3};

        System.out.println(someMethod().toString() + Arrays.toString(arr) ); // Noncompliant
    }
}
