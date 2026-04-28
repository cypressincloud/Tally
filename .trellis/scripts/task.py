#!/usr/bin/env python3
"""
Trellis 任务管理脚本
用于查看和管理项目任务
"""

import os
import sys
from pathlib import Path

TASKS_DIR = Path(".trellis/tasks")
CURRENT_TASK_FILE = TASKS_DIR / ".current-task"


def list_tasks():
    """列出所有任务"""
    if not TASKS_DIR.exists():
        print("错误: .trellis/tasks 目录不存在")
        return
    
    current_task = None
    if CURRENT_TASK_FILE.exists():
        current_task = CURRENT_TASK_FILE.read_text().strip()
    
    print("📋 任务列表:\n")
    
    tasks = sorted([d for d in TASKS_DIR.iterdir() if d.is_dir()])
    
    if not tasks:
        print("  暂无任务")
        return
    
    for task_dir in tasks:
        task_name = task_dir.name
        is_current = task_name == current_task
        marker = "→" if is_current else " "
        status = "[CURRENT]" if is_current else ""
        
        print(f"  {marker} {task_name} {status}")
        
        readme = task_dir / "README.md"
        if readme.exists():
            # 读取第一行作为简介
            with open(readme, 'r', encoding='utf-8') as f:
                for line in f:
                    if line.startswith('#') and not line.startswith('##'):
                        title = line.strip('# \n')
                        print(f"      {title}")
                        break
    
    print()


def finish_task():
    """完成当前任务"""
    if not CURRENT_TASK_FILE.exists():
        print("当前没有活动任务")
        return
    
    current_task = CURRENT_TASK_FILE.read_text().strip()
    print(f"✅ 完成任务: {current_task}")
    
    # 清除当前任务指针
    CURRENT_TASK_FILE.write_text("")
    print("当前任务指针已清除")


def archive_task(task_name):
    """归档指定任务"""
    task_dir = TASKS_DIR / task_name
    
    if not task_dir.exists():
        print(f"错误: 任务 '{task_name}' 不存在")
        return
    
    archive_dir = TASKS_DIR / "archived"
    archive_dir.mkdir(exist_ok=True)
    
    target = archive_dir / task_name
    
    if target.exists():
        print(f"警告: 归档目录中已存在 '{task_name}'")
        return
    
    task_dir.rename(target)
    print(f"✅ 任务 '{task_name}' 已归档到 .trellis/tasks/archived/")


def main():
    if len(sys.argv) < 2:
        print("用法:")
        print("  python task.py list              - 列出所有任务")
        print("  python task.py finish            - 完成当前任务")
        print("  python task.py archive <name>    - 归档指定任务")
        return
    
    command = sys.argv[1]
    
    if command == "list":
        list_tasks()
    elif command == "finish":
        finish_task()
    elif command == "archive":
        if len(sys.argv) < 3:
            print("错误: 请指定要归档的任务名称")
            return
        archive_task(sys.argv[2])
    else:
        print(f"错误: 未知命令 '{command}'")


if __name__ == "__main__":
    main()
