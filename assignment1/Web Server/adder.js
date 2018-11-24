function add(pos,val) {
    e = document.getElementById(pos);
    x = Number(e.textContent)+val;
    e.innerHTML = x;
}
