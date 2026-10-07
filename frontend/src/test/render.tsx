import { MantineProvider } from '@mantine/core';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import '../i18n';
import { cssVariablesResolver, theme } from '../theme/theme';

export function renderWithProviders(ui: ReactElement) {
  return render(
    <MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver}>
      {ui}
    </MantineProvider>,
  );
}
