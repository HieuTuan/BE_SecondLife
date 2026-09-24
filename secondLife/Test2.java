import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class Test2 {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("org.springframework.ai.chat.messages.UserMessage");
        for (Constructor<?> c : clazz.getConstructors()) {
            System.out.println(c);
        }
        System.out.println("--- Methods ---");
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals("getMedia")) {
                System.out.println(m.getReturnType().getName());
            }
        }
    }
}
