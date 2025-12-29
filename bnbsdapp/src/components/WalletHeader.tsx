import { Box, Typography } from '@mui/material'
import './WalletHeader.css'

export function WalletHeader() {
  return (
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
            <img src="BNBs.svg" className="WalletHeader-img"></img> BNBs AI Exchange
        </div>
      </Typography>
    </Box>
  )
}