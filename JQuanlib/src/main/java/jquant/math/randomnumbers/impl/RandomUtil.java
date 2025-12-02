package jquant.math.randomnumbers.impl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
public class RandomUtil {

    // number of dimensions in the alternative primitive polynomials
    public static final int maxAltDegree = 52;

    public static final List<List<Integer>> AltPrimitivePolynomials = parseInitializersTxt1("AltPrimitivePolynomials.txt");

    public static final List<List<Integer>> SLinitializers = parseInitializersTxt1("SLinitializers.txt");

    public static final List<List<Integer>> initializers = parseInitializersTxt1("initializers.txt");

    public static final List<List<Integer>> Linitializers = parseInitializersTxt1("Linitializers.txt");

    public static final List<List<Integer>> Kuoinitializers = parseInitializersTxt1("Kuoinitializers.txt");

    public static final List<List<Integer>> Kuo2initializers = parseInitializersTxt1("Kuo2initializers.txt");

    public static final List<List<Integer>> Kuo3initializers = parseInitializersTxt1("Kuo3initializers.txt");

    public static final List<List<Integer>> JoeKuoD6initializers = parseInitializersTxt1("JoeKuoD6initializers.txt");

    public static final List<List<Integer>> JoeKuoD7initializers = parseInitializersTxt1("JoeKuoD7initializers.txt");

    public static final List<List<Integer>> JoeKuoD5initializers = parseInitializersTxt1("JoeKuoD5initializers.txt");

    public static final List<List<Integer>> PrimitivePolynomials =  parseInitializersTxt1("PrimitivePolynomials.txt");

    // for reverseBits() see http://graphics.stanford.edu/~seander/bithacks.html#BitReverseTable
    public static final int[] bitReverseTable = {
            0,   128, 64,  192, 32,  160, 96,  224, 16,  144, 80,  208, 48,  176,
            112, 240, 8,   136, 72,  200, 40,  168, 104, 232, 24,  152, 88,  216,
            56,  184, 120, 248, 4,   132, 68,  196, 36,  164, 100, 228, 20,  148,
            84,  212, 52,  180, 116, 244, 12,  140, 76,  204, 44,  172, 108, 236,
            28,  156, 92,  220, 60,  188, 124, 252, 2,   130, 66,  194, 34,  162,
            98,  226, 18,  146, 82,  210, 50,  178, 114, 242, 10,  138, 74,  202,
            42,  170, 106, 234, 26,  154, 90,  218, 58,  186, 122, 250, 6,   134,
            70,  198, 38,  166, 102, 230, 22,  150, 86,  214, 54,  182, 118, 246,
            14,  142, 78,  206, 46,  174, 110, 238, 30,  158, 94,  222, 62,  190,
            126, 254, 1,   129, 65,  193, 33,  161, 97,  225, 17,  145, 81,  209,
            49,  177, 113, 241, 9,   137, 73,  201, 41,  169, 105, 233, 25,  153,
            89,  217, 57,  185, 121, 249, 5,   133, 69,  197, 37,  165, 101, 229,
            21,  149, 85,  213, 53,  181, 117, 245, 13,  141, 77,  205, 45,  173,
            109, 237, 29,  157, 93,  221, 61,  189, 125, 253, 3,   131, 67,  195,
            35,  163, 99,  227, 19,  147, 83,  211, 51,  179, 115, 243, 11,  139,
            75,  203, 43,  171, 107, 235, 27,  155, 91,  219, 59,  187, 123, 251,
            7,   135, 71,  199, 39,  167, 103, 231, 23,  151, 87,  215, 55,  183,
            119, 247, 15,  143, 79,  207, 47,  175, 111, 239, 31,  159, 95,  223,
            63,  191, 127, 255};

    public static int reverseBits(int x) {
        return (bitReverseTable[x & 0xff] << 24) | (bitReverseTable[(x >>> 8) & 0xff] << 16) |
                (bitReverseTable[(x >>> 16) & 0xff] << 8) | (bitReverseTable[(x >>> 24) & 0xff]) ;
    }

    public static int laine_karras_permutation(int x, int seed) {
        x += seed;
        x ^= x * 0x6c50b47c;
        x ^= x * 0xb82f1e52;
        x ^= x * 0xc7afe638;
        x ^= x * 0x8d22f6e6;
        return x;
    }

    public static int nested_uniform_scramble(int x, int seed) {
        x = reverseBits(x);
        x = laine_karras_permutation(x, seed);
        x = reverseBits(x);
        return x;
    }

    // the results depend a lot on the details of the hash_combine() function that is used
    // we use hash_combine() calling hash(), hash_mix() as implemented here:
    // https://github.com/boostorg/container_hash/blob/boost-1.83.0/include/boost/container_hash/hash.hpp#L560
    // https://github.com/boostorg/container_hash/blob/boost-1.83.0/include/boost/container_hash/hash.hpp#L115
    // https://github.com/boostorg/container_hash/blob/boost-1.83.0/include/boost/container_hash/detail/hash_mix.hpp#L67

    public static long local_hash_mix(long x) {
        long m = 0xe9846af9b1a615dL;
        x ^= x >>> 32;
        x *= m;
        x ^= x >>> 32;
        x *= m;
        x ^= x >>> 28;
        return x;
    }

    public static long local_hash(long v) {
        long seed = 0;
        seed = (v >>> 32) + local_hash_mix(seed);
        seed = (v & 0xFFFFFFFFL) + local_hash_mix(seed);
        return seed;
    }

    public static long local_hash_combine(long x,long v) {
        return local_hash_mix(x + 0x9e3779b9L + local_hash(v));
    }

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

    public static List<List<Integer>> parseInitializersTxt1(String data) {
        List<List<Integer>> result = new ArrayList<>();

        // 类路径下的相对路径（resources 目录为类路径根，对应目录层级直接写）
        String resourcePath = "jquant/math/randomnumbers/data/"+data;

        // 用类加载器读取资源（推荐方式，打包后仍有效）
        InputStream inputStream = RandomUtil.class.getClassLoader().getResourceAsStream(resourcePath);

        // 读取解析（逻辑不变）
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

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
        System.out.println(PrimitivePolynomials.size());
    }
}
