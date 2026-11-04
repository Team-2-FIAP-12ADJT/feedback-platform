param([string]$Source,[string]$Dest,[string]$Stamp)
Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Bitmap]::FromFile((Resolve-Path $Source))
$g = [System.Drawing.Graphics]::FromImage($img)
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
$font = New-Object System.Drawing.Font 'Consolas', 18, ([System.Drawing.FontStyle]::Bold)
$brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
$bg = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(180,0,0,0))
$size = $g.MeasureString($Stamp, $font)
$pad = 8
$x = $img.Width - $size.Width - 16
$y = 12
$g.FillRectangle($bg, $x - $pad, $y - $pad, $size.Width + 2*$pad, $size.Height + 2*$pad)
$g.DrawString($Stamp, $font, $brush, $x, $y)
$g.Dispose()
$img.Save($Dest, [System.Drawing.Imaging.ImageFormat]::Png)
$img.Dispose()
Write-Output "saved=$Dest"
