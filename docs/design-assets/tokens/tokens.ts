export const tokens = {
  color: {
    background: {
      app: "#FAFBFA",
      mainSurface: "#FFFFFF",
      subtleSurface: "#F6F8F7",
      panelSurface: "#F3F6F4",
      hoverSurface: "#F2F4F3",
      selectedSurface: "#EEF6FF",
    },
    sidebar: {
      gradientTop: "#F7F9F8",
      gradientMid: "#F1F5F3",
      gradientEnd: "#ECF2EF",
      gradient: "linear-gradient(180deg, #F7F9F8 0%, #F1F5F3 52%, #ECF2EF 100%)",
    },
    text: {
      primary: "#171A1D",
      secondary: "#4F575E",
      tertiary: "#8A9299",
      disabled: "#B8BEC3",
      inverse: "#FFFFFF",
    },
    border: {
      subtle: "#EEF0F1",
      default: "#E2E6E8",
      strong: "#CCD3D7",
      focusRing: "#8DBBFF",
    },
    action: {
      primaryBlack: "#0D0D0E",
      primaryBlackHover: "#242426",
      primaryBlackPressed: "#000000",
    },
    accent: {
      blue: "#2563EB",
      blueHover: "#1D4ED8",
      blueSoft: "#EAF2FF",
    },
    semantic: {
      success: "#3F7D58",
      successSoft: "#EAF5EE",
      warning: "#A66A1F",
      warningSoft: "#FFF4E3",
      danger: "#B54747",
      dangerSoft: "#FCECEC",
      info: "#3B6EA8",
      infoSoft: "#EAF2FB",
    },
  },
  space: {
    2: "2px",
    4: "4px",
    8: "8px",
    12: "12px",
    16: "16px",
    20: "20px",
    24: "24px",
    28: "28px",
    32: "32px",
    36: "36px",
    40: "40px",
    44: "44px",
    48: "48px",
  },
  radius: {
    buttonSmall: "8px",
    buttonDefault: "10px",
    input: "12px",
    card: "14px",
    panel: "14px",
    drawer: "18px",
    modal: "18px",
    pill: "999px",
    iconButtonCircle: "999px",
  },
  shadow: {
    popover: "0 8px 24px rgba(15, 23, 42, 0.08)",
    modal: "0 24px 64px rgba(15, 23, 42, 0.14)",
  },
  typography: {
    family: {
      sans: 'Inter, "SF Pro Display", "SF Pro Text", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    },
    display: {
      fontSize: "28px",
      lineHeight: "36px",
      fontWeight: 700,
    },
    pageTitle: {
      fontSize: "22px",
      lineHeight: "30px",
      fontWeight: 700,
    },
    sectionTitle: {
      fontSize: "18px",
      lineHeight: "26px",
      fontWeight: 650,
    },
    cardTitle: {
      fontSize: "15px",
      lineHeight: "22px",
      fontWeight: 600,
    },
    body: {
      fontSize: "14px",
      lineHeight: "22px",
      fontWeight: 400,
    },
    bodyStrong: {
      fontSize: "14px",
      lineHeight: "22px",
      fontWeight: 600,
    },
    small: {
      fontSize: "13px",
      lineHeight: "20px",
      fontWeight: 400,
    },
    caption: {
      fontSize: "12px",
      lineHeight: "18px",
      fontWeight: 400,
    },
    tableHeader: {
      fontSize: "12px",
      lineHeight: "16px",
      fontWeight: 600,
    },
    microLabel: {
      fontSize: "11px",
      lineHeight: "16px",
      fontWeight: 600,
    },
  },
} as const;

export type LabelHubTokens = typeof tokens;

