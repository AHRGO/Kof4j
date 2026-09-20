public class Run {
    public static void main(String[] args) throws Exception {
        Class.forName(args[0]).getMethod("main", String[].class)
            .invoke(null, (Object) new String[0]);
    }
}
