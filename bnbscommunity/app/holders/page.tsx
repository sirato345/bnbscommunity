'use client';

import dynamic from 'next/dynamic';
import './App.css';

const HoldersApp = dynamic(() => import('./App'), {
  ssr: false,
  loading: () => <p role="status">Loading holders...</p>,
});

export default function HoldersPage() {
  return (
    <div className="holders-content">
      <HoldersApp />
    </div>
  );
}