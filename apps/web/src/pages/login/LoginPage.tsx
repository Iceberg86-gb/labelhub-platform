import { Button, Form, Toast, Typography } from '@douyinfe/semi-ui';
import type { FormApi } from '@douyinfe/semi-ui/lib/es/form';
import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { useLogin, type LoginValues } from '../../features/auth/login/useLogin';
import { clearSession } from '../../shared/api/auth-storage';

const LOGIN_BRAND_PANEL_VARIANT: 'workflow' | 'minimal' = 'workflow';

const LOGIN_WORKFLOW_STEPS = ['任务发布', '标注作答', 'AI 预审', '人工审核', '可信导出'];

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
    <section className="login-shell login-shell--split" aria-label="Login">
      <aside className="login-brand-panel" aria-label="LabelHub">
        <Link to="/" className="login-brand-lockup" aria-label="LabelHub home">
          <span className="login-brand-mark" aria-hidden>
            LH
          </span>
          <Typography.Title heading={4} className="login-brand-title">
            LabelHub
          </Typography.Title>
        </Link>

        <div className="login-brand-copy">
          <Typography.Title heading={1} className="login-brand-headline">
            AI 辅助,人工把关
          </Typography.Title>
          <Typography.Text className="login-brand-subtitle">
            LabelHub 以 AI 预审加速标注流转,以人工裁决守住数据质量,每一条导出都可追溯。
          </Typography.Text>
        </div>

        {LOGIN_BRAND_PANEL_VARIANT === 'workflow' ? (
          <div className="login-workflow-strip" aria-hidden>
            {LOGIN_WORKFLOW_STEPS.map((step, index) => (
              <div className="login-workflow-node" key={step}>
                <span className="login-workflow-index">{String(index + 1).padStart(2, '0')}</span>
                <span className="login-workflow-label">{step}</span>
              </div>
            ))}
          </div>
        ) : null}
      </aside>

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
            label="账号"
            placeholder="请输入账号"
            rules={[{ required: true, message: '请输入账号' }]}
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
            style={{
              background: 'var(--color-primary-black)',
              borderColor: 'var(--color-primary-black)',
              color: 'var(--color-text-inverse)',
            }}
            loading={login.isPending}
            block
          >
            登录
          </Button>
        </Form>

        <Typography.Text className="register-login-link" type="tertiary">
          还没有账号？<Link to="/register">创建新账号</Link>
        </Typography.Text>
      </div>
    </section>
  );
}
