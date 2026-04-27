import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.denmarkroadassistant.app',
  appName: 'Denmark Road Assistant',
  webDir: 'dist/frontend/browser',
  server: {
    androidScheme: 'http',
    cleartext: true
  }
};

export default config;
