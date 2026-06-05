import { Button, Form, Toast, Typography } from '@douyinfe/semi-ui';
import type { FormApi } from '@douyinfe/semi-ui/lib/es/form';
import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { useRegister, type RegisterValues } from '../../features/auth/register/useRegister';
import { clearSession } from '../../shared/api/auth-storage';

const REGISTER_BRAND_PANEL_VARIANT: 'workflow' | 'minimal' = 'workflow';

const REGISTER_WORKFLOW_STEPS = ['任务发布', '标注作答', 'AI 预审', '人工审核', '可信导出'];

type RegisterFormValues = RegisterValues & {
  account?: string;
};

export function RegisterPage() {
  const register = useRegister();
  const formApiRef = useRef<FormApi<RegisterFormValues>>();

  useEffect(() => {
    clearSession();
  }, []);

  const handleSubmit = async (values: RegisterFormValues) => {
    const formElement = document.querySelector<HTMLFormElement>('form.register-form');
    const formData = formElement ? new FormData(formElement) : null;
    const account = String(formData?.get('account') || values.account || '');
    const actualValues = {
      username: account,
      displayName: account,
      email: String(formData?.get('email') || values.email || '') || undefined,
      password: String(formData?.get('password') || values.password || ''),
    };

    try {
      await register.mutateAsync(actualValues);
    } catch (error) {
      const registerError = register.normalizeError(error);

      if (registerError.field) {
        const field =
          registerError.field === 'username' || registerError.field === 'displayName' ? 'account' : registerError.field;
        formApiRef.current?.setError(field, registerError.message);
        return;
      }

      Toast.error(registerError.message);
    }
  };

  return (
    <section className="login-shell login-shell--split" aria-label="Register">
      <aside className="login-brand-panel" aria-label="LabelHub">
        <Link to="/" className="login-brand-lockup" aria-label="LabelHub home">
          <span className="login-brand-mark" aria-hidden>
            LH
          </span>
          <Typography.Title heading={4} className="login-brand-title">
            LabelHub
          </Typography.Title>
        </Link>

        <div className="login-brand-center">
          <div className="login-brand-copy">
            <Typography.Title heading={1} className="login-brand-headline">
              AI 辅助，人工把关
            </Typography.Title>
            <Typography.Text className="login-brand-subtitle">
              LabelHub 以 AI 预审加速标注流转,以人工裁决守住数据质量,每一条导出都可追溯。
            </Typography.Text>
          </div>

          {REGISTER_BRAND_PANEL_VARIANT === 'workflow' ? (
            <div className="login-workflow-strip" aria-hidden>
              {REGISTER_WORKFLOW_STEPS.map((step, index) => (
                <div className="login-workflow-node" key={step}>
                  <span className="login-workflow-index">{String(index + 1).padStart(2, '0')}</span>
                  <span className="login-workflow-label">{step}</span>
                </div>
              ))}
            </div>
          ) : null}
        </div>
      </aside>

      <div className="login-card">
        <div className="login-copy">
          <Typography.Title heading={3}>注册 LabelHub</Typography.Title>
          <Typography.Text type="tertiary">默认开通 LABELER 权限，审核权限由负责人单独授予</Typography.Text>
        </div>

        <Form<RegisterFormValues>
          className="register-form login-form"
          layout="vertical"
          getFormApi={(formApi) => {
            formApiRef.current = formApi;
          }}
          onSubmit={handleSubmit}
        >
          <Form.Input
            field="account"
            label="账号"
            placeholder="请输入账号"
            rules={[{ required: true, message: '请输入账号' }]}
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

        <Typography.Text className="register-login-link" type="tertiary">
          已有账号？<Link to="/login">返回登录</Link>
        </Typography.Text>
      </div>
    </section>
  );
}
