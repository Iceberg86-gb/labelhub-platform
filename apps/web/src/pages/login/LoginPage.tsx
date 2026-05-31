import { Button, Form, Toast, Typography } from '@douyinfe/semi-ui';
import type { FormApi } from '@douyinfe/semi-ui/lib/es/form';
import { useEffect, useRef } from 'react';
import welcomeHeroUrl from '../../../../../docs/design-assets/hero/welcome-hero.svg';
import { useLogin, type LoginValues } from '../../features/auth/login/useLogin';
import { clearSession } from '../../shared/api/auth-storage';

const demoAccounts = [
  { role: 'Owner', username: 'owner_demo' },
  { role: 'Labeler', username: 'labeler_demo' },
  { role: 'Reviewer', username: 'reviewer_demo' },
];

export function LoginPage() {
  const login = useLogin();
  const formApiRef = useRef<FormApi<LoginValues>>();

  useEffect(() => {
    clearSession();
  }, []);

  const handleSubmit = async (values: LoginValues) => {
    const formElement = document.querySelector<HTMLFormElement>('form.login-form');
    const formData = formElement ? new FormData(formElement) : null;
    const actualValues = {
      username: String(formData?.get('username') || values.username || ''),
      password: String(formData?.get('password') || values.password || ''),
    };

    try {
      await login.mutateAsync(actualValues);
    } catch (error) {
      const loginError = login.normalizeError(error);

      if (loginError.field) {
        formApiRef.current?.setError(loginError.field, loginError.message);
        return;
      }

      Toast.error(loginError.message);
    }
  };

  return (
    <section className="login-shell login-shell--codex-light" aria-label="Login">
      <div className="login-hero" aria-hidden>
        <img className="welcome-hero" src={welcomeHeroUrl} alt="" />
      </div>

      <div className="login-card">
        <div className="login-copy">
          <Typography.Title heading={3}>登录 LabelHub</Typography.Title>
          <Typography.Text type="tertiary">进入任务、标注、审核与导出工作台</Typography.Text>
        </div>

        <Form<LoginValues>
          className="login-form"
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
            field="password"
            label="密码"
            mode="password"
            placeholder="请输入密码"
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
            loading={login.isPending}
            block
          >
            登录
          </Button>
        </Form>

        <div className="login-demo-hint" aria-label="Demo accounts">
          <Typography.Text strong>演示账号</Typography.Text>
          <ul className="login-demo-list">
            {demoAccounts.map((account) => (
              <li key={account.username}>
                <span>{account.role}</span>
                <code>{account.username}</code>
              </li>
            ))}
          </ul>
          <Typography.Text type="tertiary">密码统一为: demo1234</Typography.Text>
        </div>
      </div>
    </section>
  );
}
