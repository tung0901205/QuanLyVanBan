import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../../services/auth/authLoginApi";
import {
  ApiError,
  clearTokens,
  setAccessToken,
  setRefreshToken,
} from "../../services/core/apiClient";
import { defaultRouteForRoles, getCurrentRoles } from "../../services/auth/roleUtils";
import { Icon } from "../../shared/Icon";

export const Login = () => {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleLogin = async (
    event: FormEvent<HTMLFormElement>
  ): Promise<void> => {
    event.preventDefault();

    if (loading) return;

    const normalizedUsername = username.trim();
    if (!normalizedUsername || !password) {
      setError("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
      return;
    }

    setError("");
    setLoading(true);
    clearTokens();
    sessionStorage.removeItem("user");

    try {
      const response = await login({
        username: normalizedUsername,
        password,
      });

      if (!response.accessToken) {
        throw new Error("Máy chủ không trả về access token.");
      }

      setAccessToken(response.accessToken);

      if (response.refreshToken) {
        setRefreshToken(response.refreshToken);
      }

      sessionStorage.setItem("user", JSON.stringify(response.user));

      navigate(defaultRouteForRoles(getCurrentRoles(response.user)), {
        replace: true,
      });
    } catch (err: unknown) {
      clearTokens();
      sessionStorage.removeItem("user");

      if (err instanceof ApiError && err.status === 401) {
        setError("Tài khoản hoặc mật khẩu không chính xác.");
      } else if (err instanceof ApiError && err.status === 0) {
        setError("Không thể kết nối tới máy chủ. Vui lòng kiểm tra API Gateway.");
      } else if (err instanceof ApiError) {
        setError(err.message || "Đăng nhập không thành công.");
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Đăng nhập không thành công.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="login-page">
      <section className="login-hero" aria-label="Giới thiệu hệ thống">
        <div className="login-hero__brand"><Icon name="archive" size={24} /><span>eOIS</span></div>
        <div className="login-hero__content">
          <span className="login-hero__eyebrow">Quản trị văn bản thông minh</span>
          <h1>Một không gian làm việc cho toàn bộ vòng đời văn bản.</h1>
          <p>Tiếp nhận, xử lý, phê duyệt và tra cứu ngữ nghĩa với trợ lý AI được kiểm soát theo quyền truy cập.</p>
          <div className="login-features">
            <div><Icon name="workflow" /><span>Quy trình rõ ràng, theo dõi SLA</span></div>
            <div><Icon name="shield" /><span>Phân quyền và nhật ký đầy đủ</span></div>
            <div><Icon name="search" /><span>OCR và tìm kiếm ngữ nghĩa</span></div>
          </div>
        </div>
        <small>Electronic Office Information System</small>
      </section>

      <section className="login-panel">
        <div className="login-card">
          <div className="login-card__mark"><Icon name="archive" size={25} /></div>
          <h2>Chào mừng trở lại</h2>
          <p>Đăng nhập để tiếp tục vào hệ thống quản lý văn bản.</p>

          {error && <div className="login-alert" role="alert">{error}</div>}

          <form onSubmit={handleLogin}>
            <label htmlFor="username">Tên đăng nhập</label>
            <div className="login-input">
              <Icon name="person" size={18} />
              <input id="username" name="username" type="text" autoComplete="username" value={username}
                onChange={(event) => { setUsername(event.target.value); if (error) setError(""); }}
                placeholder="Nhập tên đăng nhập" required disabled={loading} autoFocus />
            </div>

            <label htmlFor="password">Mật khẩu</label>
            <div className="login-input">
              <Icon name="lock" size={18} />
              <input id="password" name="password" type="password" autoComplete="current-password" value={password}
                onChange={(event) => { setPassword(event.target.value); if (error) setError(""); }}
                placeholder="Nhập mật khẩu" required disabled={loading} />
            </div>

            <button className="login-submit" type="submit" disabled={loading}>
              {loading ? <><span className="login-spinner" />Đang xác thực...</> : "Đăng nhập"}
            </button>
          </form>

          {import.meta.env.VITE_SHOW_DEMO_CREDENTIALS === "true" && (
            <div className="login-demo">Môi trường demo: <strong>admin</strong> / <strong>123456</strong></div>
          )}
        </div>
      </section>
    </main>
  );
};

export default Login;
