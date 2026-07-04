---
name: FamilyShield
colors:
  surface: '#f7f9fc'
  surface-dim: '#d8dadd'
  surface-bright: '#f7f9fc'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f4f7'
  surface-container: '#eceef1'
  surface-container-high: '#e6e8eb'
  surface-container-highest: '#e0e3e6'
  on-surface: '#191c1e'
  on-surface-variant: '#454652'
  inverse-surface: '#2d3133'
  inverse-on-surface: '#eff1f4'
  outline: '#767683'
  outline-variant: '#c6c5d4'
  surface-tint: '#4c56af'
  primary: '#000666'
  on-primary: '#ffffff'
  primary-container: '#1a237e'
  on-primary-container: '#8690ee'
  inverse-primary: '#bdc2ff'
  secondary: '#00639a'
  on-secondary: '#ffffff'
  secondary-container: '#51b2fe'
  on-secondary-container: '#00436a'
  tertiary: '#002104'
  on-tertiary: '#ffffff'
  tertiary-container: '#00390a'
  on-tertiary-container: '#48ab4d'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e0e0ff'
  primary-fixed-dim: '#bdc2ff'
  on-primary-fixed: '#000767'
  on-primary-fixed-variant: '#343d96'
  secondary-fixed: '#cee5ff'
  secondary-fixed-dim: '#96ccff'
  on-secondary-fixed: '#001d32'
  on-secondary-fixed-variant: '#004a75'
  tertiary-fixed: '#94f990'
  tertiary-fixed-dim: '#78dc77'
  on-tertiary-fixed: '#002204'
  on-tertiary-fixed-variant: '#005313'
  background: '#f7f9fc'
  on-background: '#191c1e'
  surface-variant: '#e0e3e6'
typography:
  headline-lg:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-sm:
    fontFamily: Hanken Grotesk
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-lg:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
  label-sm:
    fontFamily: Hanken Grotesk
    fontSize: 10px
    fontWeight: '500'
    lineHeight: 14px
  headline-lg-mobile:
    fontFamily: Hanken Grotesk
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  gutter: 16px
  margin-mobile: 16px
  margin-tablet: 24px
---

## Brand & Style
The design system is anchored in the concept of "Digital Guardianship." It balances the authoritative reliability of a security service with the warmth of a family-oriented tool. The target audience includes busy parents who require immediate, glanceable information and children who need a non-intrusive, friendly interface.

The design style follows a **Modern Corporate** approach with **Material 3 (M3)** influences. It prioritizes clarity, structural integrity, and high-quality typography over decorative elements. The UI should evoke a sense of calm and control through generous whitespace, consistent iconography, and a tactile interface that feels responsive and sturdy.

## Colors
The palette uses color as a functional signal rather than just aesthetic decoration. 

- **Primary (Deep Navy):** Used for top-level navigation, headers, and branding to establish authority and trust.
- **Secondary (Sky Blue):** The core action color. Used for buttons, active states, and interactive elements to provide a reassuring, non-aggressive touchpoint.
- **Tertiary (Mint Green):** Reserved specifically for positive status indicators, "Safe Zone" confirmations, and healthy battery levels.
- **Semantic Overrides:** Warm Orange (#F57C00) is used for low-priority alerts (e.g., low battery), while Red (#D32F2F) is strictly reserved for critical safety alerts or geofence breaches.
- **Neutrals:** A range of cool grays and off-whites are used to maintain a clean, professional canvas that allows functional colors to pop.

## Typography
This design system utilizes **Hanken Grotesk** for its contemporary, highly legible, and professional characteristics. It offers a cleaner, more refined alternative to standard Roboto while maintaining the native Android feel.

Headlines use a tighter letter-spacing and heavier weights to convey stability. Body text is optimized for readability with generous line heights to prevent eye fatigue during long configuration tasks. Labels use an increased letter spacing and medium/semibold weights to ensure they are distinct from body content, even at small scales.

## Layout & Spacing
The system uses an **8px grid** (base unit) to ensure alignment with Android's density-independent pixel (dp) logic. 

- **Layout Model:** A fluid grid system is employed. On mobile, it utilizes a 4-column structure with 16px margins. For tablets, it scales to an 8-column grid with 24px margins.
- **Rhythm:** Vertical spacing between cards or list items should typically follow the `lg` (24px) unit to maintain a clean, airy feel. Interior component spacing (e.g., label to input) should use the `xs` (4px) or `base` (8px) units.
- **Safe Areas:** Critical interactive elements must respect a 48dp minimum touch target height, regardless of their visual size.

## Elevation & Depth
The design system utilizes **Tonal Layers** as the primary method of showing depth, supplemented by soft ambient shadows for critical floating elements.

1.  **Level 0 (Surface):** The background layer using the lightest neutral color.
2.  **Level 1 (Cards):** Slightly raised via a subtle 1px border or a very low-opacity shadow (4% black, 2px blur).
3.  **Level 2 (Modals/Active Cards):** Elevated with an ambient shadow (8% black, 8px blur) to draw immediate focus.
4.  **Level 3 (Alerts/Floating Action Buttons):** Highest elevation, using a tinted shadow (Primary Navy at 15% opacity) to signify priority.

Glassmorphism is used sparingly for bottom navigation bars to provide context of the content scrolling beneath, utilizing a 20px backdrop blur.

## Shapes
The shape language is consistently **Rounded**, reflecting a modern and approachable utility. 

- **Small Components:** Checkboxes and small buttons use a 0.5rem (8px) radius.
- **Large Components:** Main cards, modals, and container elements use a 1rem (16px) radius.
- **Dynamic Elements:** Status chips and search bars use a pill-shape (full rounding) to differentiate them from structural content containers.

## Components
- **Buttons:** Primary buttons use the Secondary Sky Blue with white text. Secondary buttons use an outlined style with the Primary Navy. Action buttons for "Emergency" use a solid Red fill.
- **Chips:** Used for "Safe Zones" and "Battery Status." These should have a subtle background tint of their functional color (e.g., light green background with dark green text for "Safe").
- **Cards:** The main container for family member profiles. They should feature a clear header, a center-aligned avatar, and a "status bar" at the bottom that changes color based on the child's safety state.
- **Inputs:** Material-style "Filled" inputs with a subtle bottom-stroke. Labels should float on focus. Error states must include both a color change to Red and a descriptive icon.
- **Status Indicators:** 
    - **Safe Zone:** Circular green pulse icon.
    - **Battery:** Horizontal pill with dynamic fill; turns orange below 20%.
    - **Alerts:** A persistent banner at the top of the screen in Deep Navy with a Sky Blue "View" action button.