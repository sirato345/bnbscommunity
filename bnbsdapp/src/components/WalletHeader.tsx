import { Box, Typography } from '@mui/material'
import './WalletHeader.css'
import { useState } from 'react';

export function WalletHeader() {

  const [param] = useState<string | null>(() => {
    if (typeof window === 'undefined') return null;
    return new URLSearchParams(window.location.search).get('homepage');
  });
    
  return (
    param === 'true' ? (
      <Box
        mb={2}
        display="flex"
        justifyContent="space-between"
        alignItems="center"
        borderBottom="1px solid #EEE"
      >
        <Typography px={2} fontWeight={600} fontSize={20} color='black' textAlign='left'>
        </Typography>
      </Box>
    ) : (
      <Box
        p={2}
        mb={2}
        display="flex"
        justifyContent="space-between"
        alignItems="center"
        borderBottom="1px solid #EEE"
      >
        <Typography px={2} fontWeight={600} fontSize={20} color='black' textAlign='left'>
          <div className="WalletHeader-div">
            <img src="BNBs.svg" className="WalletHeader-img"></img> BNBs AI DEX
          </div>
        </Typography>
      </Box>
    )
  )
}