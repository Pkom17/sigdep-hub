/**
 * The official partner logos as displayed on the legacy SIGDEP hub.
 * Files live under /public/logos and are served as static assets by Vite.
 */
const partners = [
  {
    src: "/logos/logo-moh.jpg",
    alt: "Ministère de la Santé de l'Hygiène Publique et de la Couverture Maladie Universelle",
  },
  { src: "/logos/logo-pnls.png", alt: "PNLS" },
  { src: "/logos/logo-pepfar-ci.jpg", alt: "PEPFAR Côte d’Ivoire" },
  { src: "/logos/logo-dis.jpg", alt: "DIS" },
  { src: "/logos/logo-disd.jpg", alt: "DISD" },
];

export function PartnerLogos({ size = "md" }: { size?: "sm" | "md" }) {
  const h = size === "sm" ? "h-8" : "h-12";
  return (
    <div className="flex flex-wrap items-center gap-6">
      {partners.map((p) => (
        <img
          key={p.src}
          src={p.src}
          alt={p.alt}
          className={`${h} w-auto object-contain opacity-90`}
        />
      ))}
    </div>
  );
}
