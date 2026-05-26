import { Button, Form, Toast, Typography } from '@douyinfe/semi-ui';
import type { FormApi } from '@douyinfe/semi-ui/lib/es/form';
import { useRef } from 'react';
import { useLogin, type LoginValues } from '../../features/auth/login/useLogin';

export function LoginPage() {
  const login = useLogin();
  const formApiRef = useRef<FormApi<LoginValues>>();

  const handleSubmit = async (values: LoginValues) => {
    try {
      await login.mutateAsync(values);
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
    <section className="login-shell" aria-label="Login">
      <div className="login-panel">
        <div className="login-copy">
          <Typography.Title heading={3}>登录 LabelHub</Typography.Title>
          <Typography.Text type="tertiary">
            使用演示账号进入 Owner 工作台，验证任务管理链路的认证与权限边界。
          </Typography.Text>
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
            placeholder="owner_demo"
            rules={[{ required: true, message: '请输入用户名' }]}
          />
          <Form.Input
            field="password"
            label="密码"
            mode="password"
            placeholder="demo1234"
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
      </div>
    </section>
  );
}
