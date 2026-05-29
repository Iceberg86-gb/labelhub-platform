import type { ReactElement } from 'react';
import { act } from 'react';
import { createRoot } from 'react-dom/client';

type ActEnvironment = typeof globalThis & {
  IS_REACT_ACT_ENVIRONMENT?: boolean;
};

export function renderClient(element: ReactElement) {
  const actEnvironment = globalThis as ActEnvironment;
  const previousActEnvironment = actEnvironment.IS_REACT_ACT_ENVIRONMENT;
  actEnvironment.IS_REACT_ACT_ENVIRONMENT = true;

  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container);

  const render = (nextElement: ReactElement) => {
    act(() => {
      root.render(nextElement);
    });
  };

  render(element);

  return {
    container,
    rerender: render,
    text: () => container.textContent ?? '',
    html: () => container.innerHTML,
    unmount: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
      actEnvironment.IS_REACT_ACT_ENVIRONMENT = previousActEnvironment;
    },
  };
}
