import type { Config } from "tailwindcss";

const config: Config = {
    content: [
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                io: {
                    purple: "#6b00e3",
                    purple2: "#8431e2",
                    dark: "#212121",
                    white: "#ffffff",
                    light: "#f8f9fa",
                    slate: "#7a7a7a",
                    panel: "#ebebeb",
                },
            },
            boxShadow: {
                soft: "0 10px 30px rgba(0,0,0,0.12)",
            },
        },
    },
    plugins: [],
};

export default config;
