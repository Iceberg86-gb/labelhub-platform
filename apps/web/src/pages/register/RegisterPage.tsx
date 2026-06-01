import { Button, Form, Toast, Typography } from '@douyinfe/semi-ui';
import type { FormApi } from '@douyinfe/semi-ui/lib/es/form';
import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import welcomeHeroUrl from '../../../../../docs/design-assets/hero/welcome-hero.svg';
import { useRegister, type RegisterValues } from '../../features/auth/register/useRegister';
import { clearSession } from '../../shared/api/auth-storage';

export function RegisterPage() {
  const register = useRegister();
  const formApiRef = useRef<FormApi<RegisterValues>>();

  useEffect(() => {
    clearSession();
  }, []);

  const handleSubmit = async (values: RegisterValues) => {
    const formElement = document.querySelector<HTMLFormElement>('form.register-form');
    const formData = formElement ? new FormData(formElement) : null;
    const actualValues = {
      username: String(formData?.get('username') || values.username || ''),
      displayName: String(formData?.get('displayName') || values.displayName || ''),
      email: String(formData?.get('email') || values.email || '') || undefined,
      password: String(formData?.get('password') || values.password || ''),
    };

    try {
      await register.mutateAsync(actualValues);
    } catch (error) {
      const registerError = register.normalizeError(error);

      if (registerError.field) {
        formApiRef.current?.setError(registerError.field, registerError.message);
        return;
      }

      Toast.error(registerError.message);
    }
  };

  return (
    <section className="login-shell login-shell--codex-light login-shell--constrained" aria-label="Register">
      <div className="login-hero" aria-hidden>
        <img className="welcome-hero" src={welcomeHeroUrl} alt="" />
      </div>

      <div className="login-card">
        <div className="login-copy">
          <Typography.Title heading={3}>注册 LabelHub</Typography.Title>
          <Typography.Text type="tertiary">默认开通 LABELER 权限，审核权限由负责人单独授予</Typography.Text>
        </div>

        <Form<RegisterValues>
          className="register-form login-form"
          layout="vertical"
          getFormApi={(formApi) => {
            formApiRef.current = formApi;
          }}
          onSubmit={handleSubmit}
        >
          <Form.Input
            field="username"
            label="用户名"
            placeholder="请输入用户名"
            rules={[{ required: true, message: '请输入用户名' }]}
          />
          <Form.Input
            field="displayName"
            label="显示名"
            placeholder="请输入显示名"
            rules={[{ required: true, message: '请输入显示名' }]}
          />
          <Form.Input field="email" label="邮箱" placeholder="可选" />
          <Form.Input
            field="password"
            label="密码"
            mode="password"
            placeholder="至少 8 位"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码至少 8 位' },
            ]}
          />
          <Button
            className="login-submit"
            htmlType="submit"
            theme="solid"
            type="primary"
            style={{
              background: 'var(--color-primary-black)',
              borderColor: 'var(--color-primary-black)',
              color: 'var(--color-text-inverse)',
            }}
            loading={register.isPending}
            block
          >
            创建账号
          </Button>
        </Form>

        <div className="login-demo-hint register-role-note" aria-label="Registration role policy">
          <Typography.Text strong>角色策略</Typography.Text>
          <Typography.Text type="tertiary">
            注册只创建 LABELER。REVIEWER 与 SENIOR_REVIEWER 需要 Owner 或 Senior Reviewer 在用户权限页授予。
          </Typography.Text>
        </div>

        <Typography.Text className="register-login-link" type="tertiary">
          已有账号？<Link to="/login">返回登录</Link>
        </Typography.Text>
      </div>
    </section>
  );
}
