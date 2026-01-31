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
 * 支持的分集标识格式：
 * - 数字+点：1.标题、2.标题
 * - 数字+顿号：1、标题、2、标题
 * - 第X集：第1集 标题、第2集 标题
 * - 中文数字：第一集 标题、第二集 标题
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
 * @version 1.0
 */
public class ScriptSplitter {
    
    // 正则表达式：匹配所有支持的分集标识格式
    private static final String EPISODE_REGEX = 
        "(?:^|\\n)\\s*(\\d+)[\\.、]\\s*([^\\n]*?)\\s*$" +
        "|(?:^|\\n)\\s*[【《(\\[]?\\s*((?:第)?(?:[一二三四五六七八九十百]+|\\d+)\\s*集)\\s*[】》)\\]]?\\s*[:：——\\-]?\\s*([^\\n]*?)\\s*$";
    
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
            
            // 判断匹配的是哪种格式
            if (matcher.group(1) != null) {
                // 格式1: "1.标题" 或 "1、标题"
                episode.episodeNumber = Integer.parseInt(matcher.group(1));
                episode.episodeTitle = matcher.group(2) != null ? matcher.group(2).trim() : "";
            } else if (matcher.group(3) != null) {
                // 格式2: "第1集 标题" 或 "第一集 标题"
                String episodeStr = matcher.group(3);
                episode.episodeNumber = parseEpisodeNumber(episodeStr);
                episode.episodeTitle = matcher.group(4) != null ? matcher.group(4).trim() : "";
            }
            
            episode.startPos = matcher.end(); // 内容起始位置（标识行之后）
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
                ? episodes.get(i + 1).startPos  // 下一集的起始位置
                : content.length();              // 或文件末尾
            
            // 提取内容并去除首尾空白
            current.episodeContent = content.substring(current.startPos, endPos).trim();
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
            episodeObj.put("episode_name", "第" + ep.episodeNumber + "集");
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
        int startPos;           // 内容起始位置（用于切割）
        
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
        
        // 测试1：数字+点格式
        System.out.println("========== 测试1：数字+点格式 ==========");
        String test1 = "1.起初\n场1-1 日 内 房间\n剧本内容A\n\n2.逃生\n场2-1 日 外 森林\n剧本内容B";
        String result1 = splitter.splitScript(test1);
        System.out.println(result1);
        System.out.println();
        
        // 测试2：顿号格式
        System.out.println("========== 测试2：顿号格式 ==========");
        String test2 = "1、开端\n剧本内容A\n\n2、发展\n剧本内容B";
        String result2 = splitter.splitScript(test2);
        System.out.println(result2);
        System.out.println();
        
        // 测试3：第X集格式
        System.out.println("========== 测试3：第X集格式 ==========");
        String test3 = "第1集 初遇\n剧本内容A\n\n第2集 离别\n剧本内容B";
        String result3 = splitter.splitScript(test3);
        System.out.println(result3);
        System.out.println();
        
        // 测试4：中文数字
        System.out.println("========== 测试4：中文数字 ==========");
        String test4 = "第一集 序章\n剧本内容A\n\n第二集 起航\n剧本内容B";
        String result4 = splitter.splitScript(test4);
        System.out.println(result4);
        System.out.println();
        
        // 测试5：无分集标识
        System.out.println("========== 测试5：无分集标识 ==========");
        String test5 = "场1-1 日 内 房间\n这是一个没有分集标识的剧本\n对白内容...";
        String result5 = splitter.splitScript(test5);
        System.out.println(result5);
        System.out.println();
        
        // 测试6：中文数字转换
        System.out.println("========== 测试6：中文数字转换 ==========");
        ScriptSplitter testSplitter = new ScriptSplitter();
        System.out.println("一 -> " + testSplitter.chineseToNumber("一"));
        System.out.println("十 -> " + testSplitter.chineseToNumber("十"));
        System.out.println("十一 -> " + testSplitter.chineseToNumber("十一"));
        System.out.println("二十 -> " + testSplitter.chineseToNumber("二十"));
        System.out.println("二十三 -> " + testSplitter.chineseToNumber("二十三"));
        System.out.println("九十九 -> " + testSplitter.chineseToNumber("九十九"));
    }
}
