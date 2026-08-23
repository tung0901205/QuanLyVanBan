import { useEffect, useState } from "react";
import { ApiError } from "../../services/core/apiClient";
import { getCurrentUser } from "../../services/auth/authApi";
import { updateMyProfile } from "../../services/auth/meApi";

export default function Profile() {
  const [profile, setProfile] = useState({ hoTen: "", email: "", donVi: "" });
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    const loadProfile = async () => {
      try {
        const user = await getCurrentUser();
        setProfile({ hoTen: user.hoTen, email: user.email, donVi: user.donViId ? String(user.donViId) : "" });
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Không thể tải hồ sơ");
      }
    };
    loadProfile();
  }, []);

  const handleSave = async () => {
    try {
      const updated = await updateMyProfile({ hoTen: profile.hoTen, email: profile.email });
      sessionStorage.setItem("user", JSON.stringify(updated));
      setError(null);
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu thất bại");
    }
  };

  const initials = profile.hoTen
    ? profile.hoTen.split(" ").slice(-2).map((w) => w[0]).join("").toUpperCase()
    : "?";

  return (
    <section>
      <div className="topbar">
        <div className="topbar__title">
          <h1>Thông tin tài khoản</h1>
          <p>Cập nhật thông tin cá nhân của tài khoản.</p>
        </div>
        <div className="topbar__actions">
          <button className="button" type="button" onClick={handleSave}>
            Lưu thay đổi
          </button>
        </div>
      </div>

      {error && <div className="alert alert--error" style={{ marginBottom: 16 }}>{error}</div>}
      {saved && <div className="alert alert--success" style={{ marginBottom: 16 }}>Thông tin đã được lưu thành công.</div>}

      <div className="card" style={{ maxWidth: 880 }}>
        <div className="profile-header">
          <div className="profile-avatar">{initials}</div>
          <div className="profile-header-info">
            <h2>{profile.hoTen || "Người dùng"}</h2>
            <p>{profile.email}</p>
          </div>
        </div>

        <div className="form-section">
          <div className="form-section__title">Thông tin cơ bản</div>
          <div className="form-field">
            <label className="form-label">Họ và tên</label>
            <input
              className="form-control"
              value={profile.hoTen}
              onChange={(e) => setProfile({ ...profile, hoTen: e.target.value })}
            />
          </div>
          <div className="form-field">
            <label className="form-label">Email</label>
            <input
              className="form-control"
              type="email"
              value={profile.email}
              onChange={(e) => setProfile({ ...profile, email: e.target.value })}
            />
          </div>
          <div className="form-field">
            <label className="form-label">Đơn vị</label>
            <input className="form-control" value={profile.donVi} disabled />
          </div>
        </div>

        <div className="form-section" style={{ marginTop: 8 }}>
          <div className="form-section__title">Bảo mật</div>
          <div style={{ padding: "12px 0", display: "flex", alignItems: "center", gap: 12 }}>
            <span style={{ fontSize: 20 }}>🔒</span>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text-strong)" }}>Tài khoản nội bộ</div>
              <div style={{ fontSize: 12, color: "var(--text-muted)" }}>Xác thực bằng JWT, mật khẩu được bảo vệ bằng BCrypt</div>
            </div>
            <span className="badge badge--success" style={{ marginLeft: "auto" }}>Đang hoạt động</span>
          </div>
        </div>
      </div>
    </section>
  );
}
