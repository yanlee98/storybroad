import os
import json
import logging
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
from volcenginesdkarkruntime import Ark

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.FileHandler('batch_analyze.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

# 读取系统提示词
def load_system_prompt():
    """从 series_analysis.md 读取系统提示词"""
    with open('series_analysis.md', 'r', encoding='utf-8') as f:
        return f.read()

# 初始化 Ark 客户端
client = Ark(
    base_url="https://ark.cn-beijing.volces.com/api/v3",
    api_key=os.getenv('ARK_API_KEY', "1f70c7d1-384a-4246-b9b0-8f4053f7f478")
)

# 系统提示词
SYSTEM_PROMPT = load_system_prompt()

def analyze_episode(episode_file):
    """
    分析单个剧集文件
    
    Args:
        episode_file: 剧集文件路径
        
    Returns:
        dict: 包含分析结果的字典
    """
    episode_name = Path(episode_file).stem
    logger.info(f"开始分析 {episode_name}")
    
    try:
        # 读取剧集文件
        with open(episode_file, 'r', encoding='utf-8') as f:
            episode_content = f.read()
        
        # 调用 API
        completion = client.chat.completions.create(
            model="deepseek-v3-1-terminus",
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": episode_content},
            ],
            thinking={"type": "disabled"},
            max_tokens=32768,
        )
        
        response_content = completion.choices[0].message.content
        
        # 去除可能的 Markdown 代码块标记
        response_content = response_content.strip()
        if response_content.startswith('```json'):
            response_content = response_content[7:]  # 去掉 ```json
        elif response_content.startswith('```'):
            response_content = response_content[3:]  # 去掉 ```
        if response_content.endswith('```'):
            response_content = response_content[:-3]  # 去掉结尾的 ```
        response_content = response_content.strip()
        
        # 解析 JSON
        try:
            data = json.loads(response_content)
        except json.JSONDecodeError as e:
            logger.error(f"✗ {episode_name} JSON解析失败: {e}")
            logger.error(f"   返回内容: {response_content[:500]}")
            return {
                'episode': episode_name,
                'success': False,
                'error': f'JSON解析失败: {e}'
            }
        
        # 格式化 JSON
        formatted_json = json.dumps(data, ensure_ascii=False, indent=2)
        
        # 检查是否有空的 episode_backdrop
        empty_backdrop_count = 0
        total_scenes = 0
        
        if 'episode_backdrops_sequence' in data:
            total_scenes = len(data['episode_backdrops_sequence'])
            for scene in data['episode_backdrops_sequence']:
                if not scene.get('episode_backdrop') or scene.get('episode_backdrop') == []:
                    empty_backdrop_count += 1
                    logger.warning(f"⚠ {episode_name} 发现空场景ID: {scene.get('episode_backdrop_description', '无描述')[:50]}")
        
        # 保存结果
        results_dir = Path('results')
        results_dir.mkdir(exist_ok=True)
        
        output_file = results_dir / f"{episode_name}.json"
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(formatted_json)
        
        logger.info(f"✓ {episode_name} 分析完成 - 场景数: {total_scenes}, 空场景数: {empty_backdrop_count}")
        
        return {
            'episode': episode_name,
            'success': True,
            'total_scenes': total_scenes,
            'empty_backdrop_count': empty_backdrop_count,
            'output_file': str(output_file)
        }
        
    except Exception as e:
        logger.error(f"✗ {episode_name} 分析失败: {e}")
        return {
            'episode': episode_name,
            'success': False,
            'error': str(e)
        }

def main():
    """主函数：并行分析所有剧集"""
    
    # 获取所有剧集文件
    episodes_dir = Path('episodes')
    episode_files = sorted(episodes_dir.glob('EP*.txt'))
    
    if not episode_files:
        logger.error("未找到任何剧集文件")
        return
    
    logger.info(f"找到 {len(episode_files)} 个剧集文件，开始并行分析...")
    
    # 使用线程池并行处理
    results = []
    with ThreadPoolExecutor(max_workers=5) as executor:
        # 提交所有任务
        future_to_file = {
            executor.submit(analyze_episode, file): file 
            for file in episode_files
        }
        
        # 收集结果
        for future in as_completed(future_to_file):
            result = future.result()
            results.append(result)
    
    # 统计结果
    success_count = sum(1 for r in results if r['success'])
    failed_count = len(results) - success_count
    total_empty = sum(r.get('empty_backdrop_count', 0) for r in results if r['success'])
    
    logger.info("=" * 60)
    logger.info(f"分析完成！")
    logger.info(f"  成功: {success_count}/{len(results)}")
    logger.info(f"  失败: {failed_count}/{len(results)}")
    logger.info(f"  总空场景数: {total_empty}")
    
    # 列出有空场景的剧集
    episodes_with_empty = [
        r['episode'] for r in results 
        if r['success'] and r.get('empty_backdrop_count', 0) > 0
    ]
    
    if episodes_with_empty:
        logger.warning(f"以下剧集包含空场景ID: {', '.join(episodes_with_empty)}")
    else:
        logger.info("所有剧集场景ID都已正确匹配！")
    
    # 列出失败的剧集
    failed_episodes = [r['episode'] for r in results if not r['success']]
    if failed_episodes:
        logger.error(f"以下剧集分析失败: {', '.join(failed_episodes)}")

if __name__ == "__main__":
    main()
