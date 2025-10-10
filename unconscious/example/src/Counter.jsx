import {$computed, $state, $watchWithCleanup, preserveState} from "unconscious";

/**
 *
 * @param props
 * @property {string} props.title="Counter"
 * @property {number} props.value=0
 * @return {JSX.Element}
 * @constructor
 */
export function Counter(props) {
  const title = props.title || "Counter";
  const count = preserveState($state(props.value || 0));
  // 计算属性
  const double = $computed(() => count.value * 2);

  $watchWithCleanup(count, () => {
    console.log("Counter updated:", count.value);
  });

  return <div onClick.children("button")={() => count.value++}>
    {title} { count.value === 1 ? "Count" : "Counts" }: {count} (Double: {double}) <br />
    <button style={{color: "red"}}>+1</button>
  </div>
};
