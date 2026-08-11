import java.util.Scanner;

public class convert {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String str = sc.nextLine();
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);

            if (Character.isUpperCase(ch)) {
                System.out.print(Character.toLowerCase(ch));
            } else if (Character.isLowerCase(ch)) {
                System.out.print(Character.toUpperCase(ch));
            } else {
                System.out.print(ch);
            }
        }

        sc.close();
    }
}