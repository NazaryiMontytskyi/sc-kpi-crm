import { MantineProvider } from '@mantine/core';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import '../i18n';
import { cssVariablesResolver, theme } from '../theme/theme';

export function renderWithProviders(ui: ReactElement, route = '/') {
  return render(
    <MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </MantineProvider>,
  );
}
