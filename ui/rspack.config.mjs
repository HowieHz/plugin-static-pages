import { defineConfig } from '@rspack/cli';
import path from 'path';
import Icons from 'unplugin-icons/rspack';
import process from 'process';
import { VueLoaderPlugin } from 'vue-loader';
import { fileURLToPath } from 'url';
import Markdown from 'unplugin-vue-markdown/webpack';
import Shiki from '@shikijs/markdown-it';
import { UnoCSSRspackPlugin } from '@unocss/webpack/rspack';

const isProduction = process.env.NODE_ENV === 'production';
const dirname = path.dirname(fileURLToPath(import.meta.url));

const outDir = isProduction ? '../src/main/resources/console' : '../build/resources/main/console';

export default defineConfig({
  mode: isProduction ? 'production' : 'development',
  entry: {
    main: './src/index.ts',
  },
  plugins: [
    new VueLoaderPlugin(),
    Icons({
      compiler: 'vue3',
    }),
    UnoCSSRspackPlugin(),
    Markdown({
      async markdownItSetup(md) {
        md.use(await Shiki({ theme: 'github-dark' }));
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(dirname, 'src'),
    },
    extensions: ['.ts', '.js'],
  },
  output: {
    publicPath: '/plugins/static-pages/assets/console/',
    chunkFilename: '[id].js',
    cssFilename: 'style.css',
    path: path.resolve(outDir),
    library: {
      type: 'window',
      export: 'default',
      name: 'static-pages',
    },
    clean: true,
    iife: true,
  },
  optimization: {
    providedExports: false,
    realContentHash: true,
  },
  experiments: {
    css: true,
  },
  module: {
    rules: [
      {
        test: /\.ts$/,
        exclude: [/node_modules/],
        loader: 'builtin:swc-loader',
        options: {
          jsc: {
            parser: {
              syntax: 'typescript',
            },
          },
        },
        type: 'javascript/auto',
      },
      {
        test: /\.(vue|md)$/,
        loader: 'vue-loader',
        options: {
          experimentalInlineMatchResource: true,
        },
      },
      {
        test: /\.css$/i,
        use: ['style-loader', 'css-loader'],
        type: 'javascript/auto',
      },
    ],
  },
  externals: {
    vue: 'Vue',
    'vue-router': 'VueRouter',
    '@vueuse/core': 'VueUse',
    '@vueuse/components': 'VueUse',
    '@vueuse/router': 'VueUse',
    '@halo-dev/console-shared': 'HaloConsoleShared',
    '@halo-dev/components': 'HaloComponents',
    '@halo-dev/api-client': 'HaloApiClient',
    '@halo-dev/richtext-editor': 'RichTextEditor',
    axios: 'axios',
  },
});
