package jquant.math.statistics.impl;

import jquant.math.CommonUtil;

import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.ListIterator;

import static jquant.math.CommonUtil.QL_REQUIRE;
import static jquant.math.MathUtils.close_enough;

public class StatiticsUtil {
    /**
     * The discontinuous quantiles use the method (type 8) as
     * recommended by Hyndman and Fan (1996). The resulting
     * quantile estimates are approximately median-unbiased
     * regardless of the distribution of 'samples'.
     * <p>
     * If quantile function is called multiple times for the same
     * dataset, it is recommended to pre-sort the sample vector.
     */
    public static double quantile(List<Double> samples, double prob) {
        int nsample = samples.size();
        QL_REQUIRE(prob >= 0.0 && prob <= 1.0,
                "Probability has to be in [0,1].");
        QL_REQUIRE(nsample > 0, "The sample size has to be positive.");

        if (nsample == 1)
            return samples.get(0);

        // two special cases: close to boundaries
        final double a = 1. / 3, b = 2 * a / (nsample + a);
        if (prob < b)
            return Collections.min(samples); //*std::min_element(samples.begin(), samples.end());
        else if (prob > 1 - b)
            return Collections.max(samples); //*std::max_element(samples.begin(), samples.end());

        // general situation: middle region and nsample >= 2
        int index = (int) Math.floor((nsample + a) * prob + a); //static_cast < Size > (std::floor ((nsample + a) * prob + a));
        List<Double> sorted = CommonUtil.ArrayInit(index + 1);
        partialSortCopy(samples, sorted);
        // use "index & index+1"th elements to interpolate the quantile
        double weight = nsample * prob + a - index;
        return (1 - weight) * sorted.get(index - 1) + weight * sorted.get(index);
    }

    /**
     * 从源列表 (samples) 中找出最小的 K 个元素，并排序后复制到目标列表 (sorted) 中。
     * K 由目标列表的当前大小决定。
     *
     * @param samples 源 List<Double>，从中选择元素。
     * @param sorted  目标 List<Double>，必须预先设置好其大小K，用来存放结果。
     *                注意：此方法会清空 sorted 并重新填充。
     */
    public static void partialSortCopy(List<Double> samples, List<Double> sorted) {
        if (sorted == null) {
            return;
        }

        // K 是目标列表的大小，即我们想要找出的最小元素的数量。
        int k = sorted.size();
        if (k == 0) {
            return;
        }

        // 1. 使用最大堆 (Max-Heap) 来存储 K 个最小元素。
        // Collections.reverseOrder() 将 PriorityQueue 变为最大堆。
        PriorityQueue<Double> maxHeap = new PriorityQueue<>(k, Collections.reverseOrder());

        // 2. 遍历源列表 samples
        for (double sample : samples) {
            if (maxHeap.size() < k) {
                // 如果堆还没有满，直接添加元素。
                maxHeap.offer(sample);
            } else if (sample < maxHeap.peek()) {
                // 如果当前元素比堆顶（目前 K 个最小元素中的最大值）还小，
                // 那么这个元素应该被保留。
                maxHeap.poll(); // 移除堆中最大的元素
                maxHeap.offer(sample); // 添加新的更小的元素
            }
        }

        // 3. 将堆中的元素按排序后的顺序复制到目标列表 sorted。

        // 优先队列 (PriorityQueue) 默认是最小堆，虽然我们这里用了最大堆的比较器，
        // 但为了获得排序后的结果，我们可以利用它的特性：

        // 首先清空目标列表
        sorted.clear();

        // 临时列表用于存储堆中元素，然后排序
        List<Double> result = new java.util.ArrayList<>(maxHeap);

        // 因为堆中的元素是无序的，我们需要对这 K 个元素进行最后的排序（升序）。
        // 此时 List 的大小最多为 K，排序成本很低 O(K log K)。
        Collections.sort(result);

        // 将排序后的结果复制到目标列表中。
        sorted.addAll(result);

        // **注意**：如果 samples 的大小小于 k，则 sorted 中只有 maxHeap.size() 个元素。
    }

    /**
     * 模拟 std::unique 的功能：消除 List 中相邻的、满足谓词关系的重复元素。
     *
     * @param breaks 需要处理的 List<Double> (会被修改)
     */
    public static void unique(List<Double> breaks) {
        if (breaks == null || breaks.size() < 2) {
            return;
        }

        // 使用 ListIterator 进行安全的删除操作
        ListIterator<Double> current = breaks.listIterator();

        // previous 是我们保留的最后一个元素。初始化为第一个元素。
        Double previous = current.next();

        // 从第二个元素开始遍历
        while (current.hasNext()) {
            Double currentElement = current.next();

            // 检查当前元素和前一个保留的元素是否“足够接近”
            if (close_enough(previous, currentElement)) {
                // 如果相等（足够接近），则删除当前元素
                current.remove();
            } else {
                // 如果不相等，则当前元素成为下一个要比较的“前一个保留元素”
                previous = currentElement;
            }
        }
    }
}
