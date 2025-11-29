package jquant.math.randomnumbers.impl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
public class RandomUtil {

    // number of dimensions in the alternative primitive polynomials
    public static final int maxAltDegree = 52;

    public static final List<List<Integer>> AltPrimitivePolynomials = parseInitializersTxt("AltPrimitivePolynomials.txt");

    public static final List<List<Integer>> SLinitializers = parseInitializersTxt("SLinitializers.txt");

    public static final List<List<Integer>> initializers = parseInitializersTxt("initializers.txt");

    public static final List<List<Integer>> Linitializers = parseInitializersTxt("Linitializers.txt");

    public static final List<List<Integer>> Kuoinitializers = parseInitializersTxt("Kuoinitializers.txt");

    public static final List<List<Integer>> Kuo2initializers = parseInitializersTxt("Kuo2initializers.txt");

    public static final List<List<Integer>> Kuo3initializers = parseInitializersTxt("Kuo3initializers.txt");

    public static final List<List<Integer>> JoeKuoD6initializers = parseInitializersTxt("JoeKuoD6initializers.txt");

    public static final List<List<Integer>> JoeKuoD7initializers = parseInitializersTxt("JoeKuoD7initializers.txt");

    public static final List<List<Integer>> JoeKuoD5initializers = parseInitializersTxt("JoeKuoD5initializers.txt");

    private static List<List<Integer>> parseInitializersTxt(String data){
        List<List<Integer>> result = new ArrayList<>();

        // 步骤1：获取项目根目录（user.dir 就是项目根目录 D:\Quant\JQuant\JQuanlib\）
        String projectRoot = System.getProperty("user.dir");

        // 步骤2：拼接源码目录下的文件路径（直接对应你的 src/main/java 结构）
        String txtPath = projectRoot + "/src/main/java/jquant/math/randomnumbers/data/"+data;
        File targetFile = new File(txtPath);

        // 读取并解析（保留所有数字）
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty()) continue;

                String[] numStrs = trimmedLine.split("\\s*,\\s*");
                List<Integer> row = new ArrayList<>();
                for (String numStr : numStrs) {
                    if (!numStr.isEmpty()) {
                        row.add(Integer.parseInt(numStr));
                    }
                }
                if (!row.isEmpty()) result.add(row);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return result;
    }
    public static void main(String[] args) throws IOException {
        System.out.println(AltPrimitivePolynomials.size());
        System.out.println(SLinitializers.size());
        System.out.println(initializers.size());
        System.out.println(Linitializers.size());
        System.out.println(Kuoinitializers.size());
        System.out.println(Kuo2initializers.size());
        System.out.println(Kuo3initializers.size());
        System.out.println(JoeKuoD6initializers.size());
        System.out.println(JoeKuoD7initializers.size());
        System.out.println(JoeKuoD5initializers.size());
    }
}
