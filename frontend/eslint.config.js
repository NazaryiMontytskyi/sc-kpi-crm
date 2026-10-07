import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

// Hex colors are forbidden everywhere except the theme palette: colors go through Mantine theme tokens.
const hexColor = '/^#[0-9a-fA-F]{3,8}$/';
const noHexColors = [
  {
    selector: `Literal[value=${hexColor}]`,
    message: 'Hex colors are only allowed in src/theme/colors.ts. Use Mantine theme tokens.',
  },
  {
    selector: 'TemplateElement[value.raw=/#[0-9a-fA-F]{3,8}\\b/]',
    message: 'Hex colors are only allowed in src/theme/colors.ts. Use Mantine theme tokens.',
  },
];

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'src/api/generated'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: { ecmaVersion: 2023, globals: { ...globals.browser, ...globals.node } },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'no-restricted-syntax': ['error', ...noHexColors],
    },
  },
  {
    files: ['src/theme/colors.ts', '**/*.test.{ts,tsx}'],
    rules: { 'no-restricted-syntax': 'off' },
  },
  {
    files: ['scripts/**/*.mjs', 'eslint.config.js'],
    extends: [js.configs.recommended],
    languageOptions: { globals: globals.node },
  },
);
