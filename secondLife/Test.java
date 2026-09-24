import java.lang.reflect.Constructor;

public class Test {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("org.springframework.ai.chat.messages.UserMessage");
        for (Constructor<?> c : clazz.getConstructors()) {
            System.out.println(c);
        }
    }
}
