'use client';

import dynamic from 'next/dynamic';
import './App.css';

const HoldersApp = dynamic(() => import('./App'), {
  ssr: false,
});

export default function HoldersPage() {
  return (
    <div className="holders-content">
      <HoldersApp />
    </div>
  );
}