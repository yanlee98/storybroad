import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 剧本自动分集工具
 * 
 * 功能：将完整剧本按照分集标识自动切割为多个独立集数
 * 
 * 支持的分集标识格式（仅支持"第X集"格式）：
 * 
 * 基础格式：
 * - 第1集、第2集、第3集
 * - 第一集、第二集、第三集（中文数字）
 * 
 * 带标题格式（支持多种分隔符）：
 * - 第1集 标题（空格分隔）
 * - 第1集.标题（点号分隔）
 * - 第1集、标题（顿号分隔）
 * - 第1集:标题（英文冒号）
 * - 第1集：标题（中文冒号）
 * - 第1集——标题（破折号）
 * - 第1集-标题（连字符）
 * 
 * 带括号格式：
 * - 【第1集】标题（方括号）
 * - 《第1集》标题（书名号）
 * - （第1集）标题（圆括号）
 * - [第1集]标题（英文方括号）
 * 
 * 括号+分隔符组合：
 * - 【第1集】:标题、《第一集》.标题 等
 * 
 * 输出格式：
 * {
 *   "episodes": [
 *     {
 *       "episode_id": "EP1",
 *       "episode_name": "第1集",  // 只输出"第X集"，不包含标题
 *       "episode_content": "剧本内容..."
 *     }
 *   ]
 * }
 * 
 * 依赖：org.json
 * Maven: 
 * <dependency>
 *     <groupId>org.json</groupId>
 *     <artifactId>json</artifactId>
 *     <version>20231013</version>
 * </dependency>
 * 
 * @author 剧本分集系统
 * @version 2.0
 */
public class ScriptSplitter {
    
    // 正则表达式：匹配所有支持的分集标识格式
    // private static final String EPISODE_REGEX = 
    //     "(?:^|\\n)\\s*(\\d+)[\\.、]\\s*([^\\n]*?)\\s*$" +
    //     "|(?:^|\\n)\\s*[【《(\\[]?\\s*((?:第)?(?:[一二三四五六七八九十百]+|\\d+)\\s*集)\\s*[】》)\\]]?\\s*[:：——\\-]?\\s*([^\\n]*?)\\s*$";
    private static final String EPISODE_REGEX = 
    "(?:^|\\n)\\s*[【《(（\\[]?\\s*((?:第)?(?:[一二三四五六七八九十百]+|\\d+)\\s*集)\\s*[】》)）\\]]?\\s*[:：——\\-\\.、]?\\s*([^\\n]*?)\\s*$";
    
    // 中文数字映射表
    private static final Map<Character, Integer> CHINESE_DIGIT_MAP = new HashMap<>();
    static {
        CHINESE_DIGIT_MAP.put('一', 1);
        CHINESE_DIGIT_MAP.put('二', 2);
        CHINESE_DIGIT_MAP.put('三', 3);
        CHINESE_DIGIT_MAP.put('四', 4);
        CHINESE_DIGIT_MAP.put('五', 5);
        CHINESE_DIGIT_MAP.put('六', 6);
        CHINESE_DIGIT_MAP.put('七', 7);
        CHINESE_DIGIT_MAP.put('八', 8);
        CHINESE_DIGIT_MAP.put('九', 9);
        CHINESE_DIGIT_MAP.put('十', 10);
        CHINESE_DIGIT_MAP.put('百', 100);
    }
    
    /**
     * 剧本分集主方法
     * 
     * @param scriptContent 剧本文本内容
     * @return JSON字符串，格式：{"episodes": [{"episode_id": "EP1", "episode_name": "第1集", "episode_content": "..."}]}
     */
    public String splitScript(String scriptContent) {
        // 1. 文本预处理：统一换行符
        scriptContent = normalizeLineEndings(scriptContent);
        
        // 2. 使用正则查找所有分集标识
        List<Episode> episodes = findEpisodes(scriptContent);
        
        // 3. 如果没有找到分集标识，整个剧本作为一集
        if (episodes.isEmpty()) {
            episodes.add(createSingleEpisode(scriptContent));
        }
        
        // 4. 提取每集内容
        episodes = extractEpisodeContent(scriptContent, episodes);
        
        // 5. 构建JSON输出
        return buildJson(episodes);
    }
    
    /**
     * 统一换行符为 \n
     * 处理 Windows(\r\n) 和 Mac旧版(\r) 的换行符
     */
    private String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
    
    /**
     * 查找所有分集标识位置
     * 
     * @param content 剧本内容
     * @return 分集列表（包含集号、标题、起始位置）
     */
    private List<Episode> findEpisodes(String content) {
        List<Episode> episodes = new ArrayList<>();
        
        Pattern pattern = Pattern.compile(EPISODE_REGEX, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            Episode episode = new Episode();
            
            // 当前正则只支持"第X集"格式，捕获组：
            // group(1): "第1集" 或 "第一集" 或 "1集"
            // group(2): 标题（可选）
            if (matcher.group(1) != null) {
                String episodeStr = matcher.group(1);
                episode.episodeNumber = parseEpisodeNumber(episodeStr);
                episode.episodeTitle = matcher.group(2) != null ? matcher.group(2).trim() : "";
            }
            
            // 保存标识行的起始位置（用于确定上一集的结束位置）
            episode.lineStartPos = matcher.start();
            
            // 找到标识行之后的第一个换行符，从下一行开始作为内容起始位置
            int matchEnd = matcher.end();
            int nextLineStart = content.indexOf('\n', matchEnd);
            episode.startPos = (nextLineStart != -1) ? nextLineStart + 1 : matchEnd;
            episodes.add(episode);
        }
        
        return episodes;
    }
    
    /**
     * 提取每集具体内容
     * 
     * @param content 剧本全文
     * @param episodes 分集列表
     * @return 填充了内容的分集列表
     */
    private List<Episode> extractEpisodeContent(String content, List<Episode> episodes) {
        for (int i = 0; i < episodes.size(); i++) {
            Episode current = episodes.get(i);
            
            // 确定当前集的结束位置
            int endPos = (i + 1 < episodes.size()) 
                ? episodes.get(i + 1).lineStartPos  // 下一集标识行的起始位置
                : content.length();                  // 或文件末尾
            
            // 提取内容（包含标识行）并去除首尾空白
            current.episodeContent = content.substring(current.lineStartPos, endPos).trim();
        }
        return episodes;
    }
    
    /**
     * 创建单集（整个剧本作为第1集）
     * 用于没有识别到分集标识的情况
     */
    private Episode createSingleEpisode(String content) {
        Episode episode = new Episode();
        episode.episodeNumber = 1;
        episode.episodeTitle = "";
        episode.episodeContent = content.trim();
        return episode;
    }
    
    /**
     * 构建JSON输出
     * 
     * @param episodes 分集列表
     * @return JSON字符串
     */
    private String buildJson(List<Episode> episodes) {
        JSONObject result = new JSONObject();
        JSONArray episodesArray = new JSONArray();
        
        for (Episode ep : episodes) {
            JSONObject episodeObj = new JSONObject();
            episodeObj.put("episode_id", "EP" + ep.episodeNumber);
            episodeObj.put("episode_name", "第" + ep.episodeNumber + "集");  // 只显示"第X集"
            episodeObj.put("episode_content", ep.episodeContent);
            episodesArray.put(episodeObj);
        }
        
        result.put("episodes", episodesArray);
        return result.toString(2); // 格式化输出，缩进2空格
    }
    
    /**
     * 解析集号（支持中文数字）
     * 
     * @param episodeStr 集号字符串，如 "第1集"、"第一集"、"1"、"一"
     * @return 阿拉伯数字集号
     */
    private int parseEpisodeNumber(String episodeStr) {
        // 移除"第"和"集"
        episodeStr = episodeStr.replace("第", "").replace("集", "").trim();
        
        // 如果是纯数字，直接解析
        if (episodeStr.matches("\\d+")) {
            return Integer.parseInt(episodeStr);
        }
        
        // 如果是中文数字，调用转换方法
        return chineseToNumber(episodeStr);
    }
    
    /**
     * 中文数字转阿拉伯数字
     * 支持：一~九十九
     * 
     * 示例：
     * - "一" -> 1
     * - "十" -> 10
     * - "十一" -> 11
     * - "二十" -> 20
     * - "二十三" -> 23
     * - "九十九" -> 99
     * 
     * @param chinese 中文数字字符串
     * @return 阿拉伯数字
     */
    private int chineseToNumber(String chinese) {
        chinese = chinese.replace("第", "").replace("集", "").trim();
        
        // 如果是纯数字，直接返回
        if (chinese.matches("\\d+")) {
            return Integer.parseInt(chinese);
        }
        
        // 单个字符
        if (chinese.length() == 1) {
            return CHINESE_DIGIT_MAP.getOrDefault(chinese.charAt(0), 1);
        }
        
        int result = 0;
        int temp = 0;
        
        for (int i = 0; i < chinese.length(); i++) {
            char c = chinese.charAt(i);
            if (c == '十') {
                // 处理"十"的特殊情况
                if (temp == 0) {
                    temp = 1; // "十"单独出现表示10
                }
                result += temp * 10;
                temp = 0;
            } else if (c == '百') {
                // 处理"百"
                if (temp == 0) {
                    temp = 1;
                }
                result += temp * 100;
                temp = 0;
            } else {
                // 普通数字
                temp = CHINESE_DIGIT_MAP.getOrDefault(c, 0);
            }
        }
        
        // 加上最后剩余的个位数
        result += temp;
        
        return result > 0 ? result : 1; // 如果解析失败，默认返回1
    }
    
    /**
     * Episode 数据类
     * 存储单集的信息
     */
    static class Episode {
        int episodeNumber;      // 集号
        String episodeTitle;    // 集标题（可能为空）
        String episodeContent;  // 集内容
        int lineStartPos;       // 标识行起始位置
        int startPos;           // 内容起始位置（标识行的下一行）
        
        @Override
        public String toString() {
            return "Episode{" +
                    "number=" + episodeNumber +
                    ", title='" + episodeTitle + '\'' +
                    ", contentLength=" + (episodeContent != null ? episodeContent.length() : 0) +
                    '}';
        }
    }
    
    // ==================== 测试代码 ====================
    
    /**
     * 测试方法
     */
    public static void main(String[] args) {
        ScriptSplitter splitter = new ScriptSplitter();
        
        System.out.println("================================================================================");
        System.out.println("                      剧本分集工具 - 支持格式测试");
        System.out.println("================================================================================");
        System.out.println();
        
        // 测试1：基础"第X集"格式（无标题）
        System.out.println("========== 测试1：基础格式（无标题） ==========");
        String test1 = "第1集\n场1-1 日 内 房间\n剧本内容A\n\n第2集\n场2-1 日 外 森林\n剧本内容B";
        String result1 = splitter.splitScript(test1);
        System.out.println(result1);
        System.out.println();
        
        // 测试2：带空格标题
        System.out.println("========== 测试2：第X集 标题（空格分隔） ==========");
        String test2 = "第1集 初遇\n剧本内容A\n\n第2集 离别\n剧本内容B";
        String result2 = splitter.splitScript(test2);
        System.out.println(result2);
        System.out.println();
        
        // 测试3：带点号标题
        System.out.println("========== 测试3：第X集.标题（点号分隔） ==========");
        String test3 = "第1集.起初\n剧本内容A\n\n第2集.发展\n剧本内容B";
        String result3 = splitter.splitScript(test3);
        System.out.println(result3);
        System.out.println();
        
        // 测试4：带顿号标题
        System.out.println("========== 测试4：第X集、标题（顿号分隔） ==========");
        String test4 = "第1集、序幕\n剧本内容A\n\n第二集、尾声\n剧本内容B";
        String result4 = splitter.splitScript(test4);
        System.out.println(result4);
        System.out.println();
        
        // 测试5：带冒号标题
        System.out.println("========== 测试5：第X集：标题（英文冒号分隔） ==========");
        String test5 = "第1集:开始\n剧本内容A\n\n第2集:结束\n剧本内容B";
        String result5 = splitter.splitScript(test5);
        System.out.println(result5);
        System.out.println();
        
        // 测试6：带中文冒号标题
        System.out.println("========== 测试6：第X集：标题（中文冒号） ==========");
        String test6 = "第1集：序幕\n剧本内容A\n\n第2集：终章\n剧本内容B";
        String result6 = splitter.splitScript(test6);
        System.out.println(result6);
        System.out.println();
        
        // 测试7：带破折号标题
        System.out.println("========== 测试7：第X集——标题（破折号） ==========");
        String test7 = "第1集——相遇\n剧本内容A\n\n第2集——分离\n剧本内容B";
        String result7 = splitter.splitScript(test7);
        System.out.println(result7);
        System.out.println();
        
        // 测试8：带连字符标题
        System.out.println("========== 测试8：第X集-标题（连字符） ==========");
        String test8 = "第1集-初始\n剧本内容A\n\n第2集-完结\n剧本内容B";
        String result8 = splitter.splitScript(test8);
        System.out.println(result8);
        System.out.println();
        
        // 测试9：带方括号
        System.out.println("========== 测试9：【第X集】标题（方括号） ==========");
        String test9 = "【第1集】起点\n剧本内容A\n\n【第2集】终点\n剧本内容B";
        String result9 = splitter.splitScript(test9);
        System.out.println(result9);
        System.out.println();
        
        // 测试10：带书名号
        System.out.println("========== 测试10：《第X集》标题（书名号） ==========");
        String test10 = "《第1集》开篇\n剧本内容A\n\n《第2集》收尾\n剧本内容B";
        String result10 = splitter.splitScript(test10);
        System.out.println(result10);
        System.out.println();
        
        // 测试11：带圆括号
        System.out.println("========== 测试11：（第X集）标题（圆括号） ==========");
        String test11 = "（第1集）启程\n剧本内容A\n\n（第2集）归来\n剧本内容B";
        String result11 = splitter.splitScript(test11);
        System.out.println(result11);
        System.out.println();
        
        // 测试12：带方括号（英文）
        System.out.println("========== 测试12：[第X集]标题（英文方括号） ==========");
        String test12 = "[第1集]序曲\n剧本内容A\n\n[第2集]尾声\n剧本内容B";
        String result12 = splitter.splitScript(test12);
        System.out.println(result12);
        System.out.println();
        
        // 测试13：中文数字（含复合数字）
        System.out.println("========== 测试13：第一集、第十一集、第十二集（中文数字） ==========");
        String test13 = "第一集 序章\n剧本内容A\n\n第十一集 转折\n剧本内容B\n\n第十二集 高潮\n剧本内容C";
        String result13 = splitter.splitScript(test13);
        System.out.println(result13);
        System.out.println();
        
        // 测试14：无分集标识
        System.out.println("========== 测试14：无分集标识（默认第1集） ==========");
        String test14 = "场1-1 日 内 房间\n这是一个没有分集标识的剧本\n对白内容...";
        String result14 = splitter.splitScript(test14);
        System.out.println(result14);
        System.out.println();
        
        // 测试15：中文数字转换功能测试
        System.out.println("========== 测试15：中文数字转换功能 ==========");
        System.out.println("基础数字：");
        System.out.println("  一 -> " + splitter.chineseToNumber("一") + 
                         ", 五 -> " + splitter.chineseToNumber("五") + 
                         ", 九 -> " + splitter.chineseToNumber("九"));
        System.out.println("十位数：");
        System.out.println("  十 -> " + splitter.chineseToNumber("十") + 
                         ", 十一 -> " + splitter.chineseToNumber("十一") + 
                         ", 十二 -> " + splitter.chineseToNumber("十二") + 
                         ", 十五 -> " + splitter.chineseToNumber("十五"));
        System.out.println("二十位数：");
        System.out.println("  二十 -> " + splitter.chineseToNumber("二十") + 
                         ", 二十一 -> " + splitter.chineseToNumber("二十一") + 
                         ", 二十三 -> " + splitter.chineseToNumber("二十三"));
        System.out.println("更大数字：");
        System.out.println("  五十 -> " + splitter.chineseToNumber("五十") + 
                         ", 九十九 -> " + splitter.chineseToNumber("九十九"));
        System.out.println();
        
        System.out.println("================================================================================");
        
        // 测试16：真实剧本文件
        System.out.println("========== 测试16：真实剧本文件《绝境先知-黄金瞳》 ==========");
        try {
            String realScript = new String(
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("绝境先知-黄金瞳.txt")),
                "UTF-8"
            );
            String realResult = splitter.splitScript(realScript);
            
            // 只显示每集的基本信息，不显示完整内容
            org.json.JSONObject jsonObj = new org.json.JSONObject(realResult);
            org.json.JSONArray episodes = jsonObj.getJSONArray("episodes");
            
            System.out.println("✓ 共识别 " + episodes.length() + " 集：");
            for (int i = 0; i < episodes.length(); i++) {
                org.json.JSONObject ep = episodes.getJSONObject(i);
                String content = ep.getString("episode_content");
                System.out.println("  - " + ep.getString("episode_name") + 
                                 " (内容长度: " + content.length() + " 字符)");
            }
        } catch (Exception e) {
            System.out.println("✗ 无法读取剧本文件：" + e.getMessage());
        }
        
        System.out.println();
        System.out.println("================================================================================");
        System.out.println("                               测试完成");
        System.out.println("================================================================================");
    }
}
