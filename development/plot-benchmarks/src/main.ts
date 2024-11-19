import { mount } from 'svelte';
import './assets/overrides.css';
import App from './lib/App.svelte';

const app = mount(App, { target: document.getElementById("app")!! });

export default app;
