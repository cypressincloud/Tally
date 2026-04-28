#!/usr/bin/env python3
"""
Trellis 上下文查看脚本
用于快速查看项目当前状态
"""

import os
import sys
from pathlib import Path
from datetime import datetime

TASKS_DIR = Path(".trellis/tasks")
CURRENT_TASK_FILE = TASKS_DIR / ".current-task"
WORKSPACE_DIR = Path(".trellis/workspace")


def get_current_task():
    """获取当前任务信息"""
    if not CURRENT_TASK_FILE.exists():
        return None, "无当前任务"
    
    task_name = CURRENT_TASK_FILE.read_text().strip()
    
    if not task_name:
        return None, "无当前任务"
    
    task_dir = TASKS_DIR / task_name
    if not task_dir.exists():
        return None, f"任务目录不存在: {task_name}"
    
    return task_name, task_dir


def get_latest_journal():
    """获取最新的工作日志"""
    if not WORKSPACE_DIR.exists():
        return None
    
    journals = sorted(WORKSPACE_DIR.glob("journal-*.md"), reverse=True)
    
    if not journals:
        return None
    
    return journals[0]


def show_context_record():
    """显示项目上下文记录"""
    print("=" * 60)
    print("📊 Budget App - 项目上下文")
    print("=" * 60)
    print()
    
    # 显示当前任务
    print("📌 当前任务:")
    task_name, task_info = get_current_task()
    
    if task_name:
        print(f"  → {task_name}")
        
        if isinstance(task_info, Path):
            readme = task_info / "README.md"
            if readme.exists():
                print()
                print("  任务详情:")
                with open(readme, 'r', encoding='utf-8') as f:
                    lines = f.readlines()[:10]  # 只显示前10行
                    for line in lines:
                        print(f"    {line.rstrip()}")
    else:
        print(f"  {task_info}")
    
    print()
    print("-" * 60)
    print()
    
    # 显示最新日志
    print("📝 最新工作日志:")
    latest_journal = get_latest_journal()
    
    if latest_journal:
        print(f"  → {latest_journal.name}")
        print()
        print("  内容摘要:")
        with open(latest_journal, 'r', encoding='utf-8') as f:
            lines = f.readlines()[:15]  # 只显示前15行
            for line in lines:
                print(f"    {line.rstrip()}")
    else:
        print("  暂无工作日志")
    
    print()
    print("=" * 60)


def show_context_summary():
    """显示项目上下文摘要"""
    task_name, _ = get_current_task()
    latest_journal = get_latest_journal()
    
    print("项目: Budget App")
    print(f"当前任务: {task_name or '无'}")
    print(f"最新日志: {latest_journal.name if latest_journal else '无'}")


def main():
    mode = "record"
    
    if len(sys.argv) > 1:
        if sys.argv[1] == "--mode" and len(sys.argv) > 2:
            mode = sys.argv[2]
    
    if mode == "record":
        show_context_record()
    elif mode == "summary":
        show_context_summary()
    else:
        print(f"错误: 未知模式 '{mode}'")
        print("用法: python get_context.py [--mode record|summary]")


if __name__ == "__main__":
    main()
