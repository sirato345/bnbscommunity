import './index.css'
import App from './App.tsx'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ReactDOM from 'react-dom/client'
import { WalletHeader } from './components/WalletHeader.tsx'
import { WalletProvider } from './providers/WalletProvider.tsx'

const queryClient = new QueryClient()

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <QueryClientProvider client={queryClient}>
    <WalletProvider>
      <WalletHeader />
      <App />
    </WalletProvider>
  </QueryClientProvider>
)

