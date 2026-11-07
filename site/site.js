// Scroll reveal (VISION.md §6: fade + 8 px drift on enter) and the hero speck field (§9).
// The page is complete without this file; it only adds motion.
(function () {
  "use strict";

  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // Reveal sections as they scroll into view. style.css hides .reveal only under html.js.
  if ("IntersectionObserver" in document.defaultView && !reduced) {
    document.documentElement.classList.add("js");
    var io = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (e) {
          if (e.isIntersecting) {
            e.target.classList.add("visible");
            io.unobserve(e.target);
          }
        });
      },
      { rootMargin: "0px 0px -10% 0px" }
    );
    document.querySelectorAll(".reveal").forEach(function (el) {
      io.observe(el);
    });
  }

  // Sparse specks behind the hero. Hidden in light mode by CSS; static under reduced motion.
  var field = document.querySelector(".specks");
  if (!field) return;
  var count = Math.min(40, Math.round(window.innerWidth / 30));
  var frag = document.createDocumentFragment();
  for (var i = 0; i < count; i++) {
    var s = document.createElement("span");
    s.style.left = Math.random() * 100 + "%";
    s.style.top = 20 + Math.random() * 80 + "%";
    s.style.setProperty("--o", (0.15 + Math.random() * 0.25).toFixed(2));
    s.style.setProperty("--d", 30 + Math.random() * 30 + "s");
    s.style.setProperty("--delay", -Math.random() * 60 + "s");
    s.style.setProperty("--dx", (Math.random() * 40 - 20).toFixed(0) + "px");
    var size = 2 + Math.random() * 2;
    s.style.width = s.style.height = size.toFixed(1) + "px";
    frag.appendChild(s);
  }
  field.appendChild(frag);
})();
