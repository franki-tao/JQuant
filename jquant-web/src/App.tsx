import React, { useEffect, useState } from 'react';
import { api } from './api/client';
import type { HealthResponse } from './types/api';
import { BondWorkbench } from './components/BondWorkbench';
import { OptionLab } from './components/OptionLab';

type ActiveTab = 'bonds' | 'options';

export const App: React.FC = () => {
  const [tab, setTab] = useState<ActiveTab>('bonds');
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [offline, setOffline] = useState(false);

  useEffect(() => {
    api
      .getHealth()
      .then((res) => {
        setHealth(res);
        setOffline(false);
      })
      .catch(() => setOffline(true));
  }, []);

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand-mark">JQ</div>
          <div>
            <div className="brand-title">JQuant Workbench</div>
            <div className="brand-sub">Quantitative Pricing &amp; Risk Studio · Powered by JQuanlib</div>
          </div>
        </div>

        <nav className="nav-tabs" aria-label="Pricing modules">
          <button
            type="button"
            className={`nav-tab ${tab === 'bonds' ? 'active' : ''}`}
            onClick={() => setTab('bonds')}
          >
            Fixed-Rate Bonds
          </button>
          <button
            type="button"
            className={`nav-tab ${tab === 'options' ? 'active' : ''}`}
            onClick={() => setTab('options')}
          >
            Option Engine Lab
          </button>
        </nav>

        <div className="topbar-right">
          <a
            href="/swagger-ui.html"
            target="_blank"
            rel="noreferrer"
            className="swagger-link"
          >
            Swagger / OpenAPI ↗
          </a>
          <span className={`status-pill ${offline ? 'down' : 'up'}`}>
            <span className="status-dot" />
            {offline ? 'API Offline' : `JVM ${health?.javaVersion ?? '17'} · UP`}
          </span>
        </div>
      </header>

      <main className="main-content">
        {tab === 'bonds' ? <BondWorkbench /> : <OptionLab />}
      </main>
    </div>
  );
};

export default App;
