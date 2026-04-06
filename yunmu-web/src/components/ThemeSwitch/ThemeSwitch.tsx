import { useEffect, useState } from "react";
import "./index.css"

export default function ThemeSwitch() {
  const [isDark, setIsDark] = useState(false);

  // 初始化时读取当前 html 标签的状态
  useEffect(() => {
    const isDarkMode = document.documentElement.classList.contains("dark");
    setIsDark(isDarkMode);
  }, []);

  // 切换逻辑：同步修改状态和 html 标签 class
  const toggleTheme = () => {
    if (isDark) {
      document.documentElement.classList.remove("dark");
      setIsDark(false);
    } else {
      document.documentElement.classList.add("dark");
      setIsDark(true);
    }
  };

  return (
    <button
      role="switch"
      aria-checked={isDark}
      className="theme-switch"
      onClick={toggleTheme}
      aria-label="切换深色模式"
    >
      <div className="switch-handle" />
    </button>
  );
}
